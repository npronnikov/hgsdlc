package ru.hgd.sdlc.settings.application;

record UpsertOutcome(int inserted, int updated, int skipped) {

    static UpsertOutcome insertedOne() {
        return new UpsertOutcome(1, 0, 0);
    }

    static UpsertOutcome updatedOne() {
        return new UpsertOutcome(0, 1, 0);
    }

    static UpsertOutcome skippedOne() {
        return new UpsertOutcome(0, 0, 1);
    }
}
