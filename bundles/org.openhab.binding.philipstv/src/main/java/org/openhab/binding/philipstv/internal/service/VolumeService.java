/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.philipstv.internal.service;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.philipstv.internal.ConnectionManager;
import org.openhab.binding.philipstv.internal.handler.PhilipsTvHandler;
import org.openhab.binding.philipstv.internal.service.api.PhilipsTvService;
import org.openhab.binding.philipstv.internal.service.model.keycode.KeyCodeDto;
import org.openhab.binding.philipstv.internal.service.model.volume.VolumeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.openhab.binding.philipstv.internal.ConnectionManager.OBJECT_MAPPER;
import static org.openhab.binding.philipstv.internal.PhilipsTvBindingConstants.CHANNEL_MUTE;
import static org.openhab.binding.philipstv.internal.PhilipsTvBindingConstants.CHANNEL_VOLUME;
import static org.openhab.binding.philipstv.internal.PhilipsTvBindingConstants.KEY_CODE_PATH;
import static org.openhab.binding.philipstv.internal.PhilipsTvBindingConstants.TV_NOT_LISTENING_MSG;
import static org.openhab.binding.philipstv.internal.PhilipsTvBindingConstants.TV_OFFLINE_MSG;
import static org.openhab.binding.philipstv.internal.PhilipsTvBindingConstants.VOLUME_PATH;
import static org.openhab.binding.philipstv.internal.service.KeyCode.KEY_MUTE;

/**
 * The {@link VolumeService} is responsible for handling volume commands, which are sent to the
 * volume channel or mute channel.
 *
 * @author Benjamin Meyer - Initial contribution
 */
public class VolumeService implements PhilipsTvService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PhilipsTvHandler handler;

    private final ConnectionManager connectionManager;

    public VolumeService(PhilipsTvHandler handler, ConnectionManager connectionManager) {
        this.handler = handler;
        this.connectionManager = connectionManager;
    }

    @Override
    public void handleCommand(String channel, Command command) {
        if (command instanceof RefreshType) {
            if (CHANNEL_VOLUME.equals(channel)) {
                try {
                    VolumeDto volumeDto = getVolume();
                    handler.postUpdateChannel(CHANNEL_VOLUME, new DecimalType(volumeDto.getCurrentVolume()));
                    handler.postUpdateChannel(CHANNEL_MUTE, volumeDto.isMuted() ? OnOffType.ON : OnOffType.OFF);
                } catch (Exception e) {
                    if (isTvOfflineException(e)) {
                        logger.warn("Could not refresh TV volume: TV is offline.");
                        handler.postUpdateThing(ThingStatus.OFFLINE, ThingStatusDetail.NONE, TV_OFFLINE_MSG);
                    } else if (isTvNotListeningException(e)) {
                        handler.postUpdateThing(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                TV_NOT_LISTENING_MSG);
                    } else {
                        logger.warn("Error retrieving the Volume: {}", e.getMessage(), e);
                    }
                }
            }
        } else if (command instanceof DecimalType) {
            try {
                setVolume(command);
                handler.postUpdateChannel(CHANNEL_VOLUME, (DecimalType) command);
            } catch (Exception e) {
                if (isTvOfflineException(e)) {
                    logger.warn("Could not execute command for TV volume: TV is offline.");
                    handler.postUpdateThing(ThingStatus.OFFLINE, ThingStatusDetail.NONE, TV_OFFLINE_MSG);
                } else {
                    logger.warn("Error during the setting of Volume: {}", e.getMessage(), e);
                }
            }
        } else if (CHANNEL_MUTE.equals(channel) && (command instanceof OnOffType)) {
            try {
                setMute();
            } catch (Exception e) {
                if (isTvOfflineException(e)) {
                    logger.warn("Could not execute Mute command: TV is offline.");
                    handler.postUpdateThing(ThingStatus.OFFLINE, ThingStatusDetail.NONE, TV_OFFLINE_MSG);
                } else {
                    logger.warn("Unknown error occurred during setting of Mute: {}", e.getMessage(), e);
                }
            }
        } else {
            logger.warn("Unknown command: {} for Channel {}", command, channel);
        }
    }

    private VolumeDto getVolume() throws IOException {
        String jsonContent = connectionManager.doHttpsGet(VOLUME_PATH);
        return OBJECT_MAPPER.readValue(jsonContent, VolumeDto.class);
    }

    private void setVolume(Command command) throws IOException {
        VolumeDto volumeDto = new VolumeDto();
        volumeDto.setMuted(false);
        volumeDto.setCurrentVolume(command.toString());
        String volumeJson = OBJECT_MAPPER.writeValueAsString(volumeDto);
        logger.debug("Set json volume: {}", volumeJson);
        connectionManager.doHttpsPost(VOLUME_PATH, volumeJson);
    }

    private void setMute() throws IOException {
        // We just sent the KEY_MUTE and dont bother what was actually requested
        KeyCodeDto keyCodeDto = new KeyCodeDto();
        keyCodeDto.setKey(KEY_MUTE);
        String muteJson = OBJECT_MAPPER.writeValueAsString(keyCodeDto);
        logger.debug("Set json mute state: {}", muteJson);
        connectionManager.doHttpsPost(KEY_CODE_PATH, muteJson);
    }
}
