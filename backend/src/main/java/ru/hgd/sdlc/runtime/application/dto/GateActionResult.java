package ru.hgd.sdlc.runtime.application.dto;

import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

public record GateActionResult(
        GateInstanceEntity gate,
        RunEntity run
) {}

