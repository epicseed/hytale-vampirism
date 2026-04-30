package com.epicseed.epiccore.skill.runtime;

import java.util.Map;

@FunctionalInterface
public interface AbilityTargetResolver<CTX, TARGET> {
    ResolvedTargets<TARGET> resolve(Map<String, Object> targeting, CTX context);
}
