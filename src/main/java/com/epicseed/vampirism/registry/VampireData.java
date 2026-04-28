package com.epicseed.vampirism.registry;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted data for the vampire registry.
 * When VampireDefaultEnabled=true in config, entries are EXCLUDED players (non-vampires).
 * When VampireDefaultEnabled=false, entries are INCLUDED players (vampires).
 */
public class VampireData {

    private List<String> entries = new ArrayList<>();

    public VampireData() {}

    public List<String> getEntries() {
        return entries;
    }

    public void setEntries(List<String> entries) {
        this.entries = new ArrayList<>(entries);
    }
}
