package com.izimi.aiplayermod.hippocampus.storage;

import com.izimi.aiplayermod.amygdala.learning.ObservedSequence;
import com.izimi.aiplayermod.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TrialStorage {

    private final Path trialsDir;

    public TrialStorage(Path botDir) {
        this.trialsDir = botDir.resolve("memory").resolve("trials");
        try {
            Files.createDirectories(trialsDir);
        } catch (IOException ignored) {}
    }

    public void save(ObservedSequence sequence) {
        Path path = trialsDir.resolve(sequence.id() + ".json");
        JsonUtil.writeToFileSafeAtomic(path, sequence);
    }

    public List<ObservedSequence> loadAll() {
        List<ObservedSequence> result = new ArrayList<>();
        try (var stream = Files.list(trialsDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        ObservedSequence seq = JsonUtil.readFromFileSafe(p, ObservedSequence.class);
                        if (seq != null) result.add(seq);
                    });
        } catch (IOException ignored) {}
        return result;
    }

    public boolean delete(String id) {
        Path path = trialsDir.resolve(id + ".json");
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }
}
