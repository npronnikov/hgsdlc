package ru.hgd.sdlc.runtime.domain;

public enum RunPublishMode {
    BRANCH,
    // Legacy value for older runs created before mode rename.
    LOCAL,
    PR
}
