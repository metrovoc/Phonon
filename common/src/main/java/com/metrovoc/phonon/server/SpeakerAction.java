package com.metrovoc.phonon.server;

/** Everything a player can ask of a speaker and the library from its screen. */
public enum SpeakerAction {
    PLAY_TRACK, PLAY_NEXT, JUMP_TO, REMOVE_FROM_QUEUE,
    PAUSE, RESUME, STOP, NEXT, PREVIOUS, CYCLE_REPEAT, TOGGLE_SHUFFLE,
    CREATE_STATION, TUNE, DELETE_STATION,
    CREATE_PLAYLIST, PLAY_PLAYLIST, ADD_TO_PLAYLIST, REMOVE_FROM_PLAYLIST, DELETE_PLAYLIST,
    ADD_LINK, DELETE_TRACK
}
