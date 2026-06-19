package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.brainstem.action.ActionSorter;
import com.izimi.eagent.brainstem.action.WorkingMemoryPool;
import com.izimi.eagent.brainstem.perception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MetaSchedulerActionSorterPipelineTest {
    @TempDir
    Path tempDir;

    @Test @DisplayName("pipeline not bound when components missing")
    void notBoundByDefault() {
        var ms = new MetaScheduler(null);
        assertFalse(ms.isActionSorterPipelineBound());
    }

    @Test @DisplayName("pipeline bound after setting all components")
    void boundAfterSetting() {
        var ms = new MetaScheduler(null);
        var ws = new WorldScanner();
        var sm = new SalienceMap(new ValueRegistry(tempDir));
        var ar = new AffordanceRouter();
        var wmp = new WorkingMemoryPool();
        var as = new ActionSorter(wmp);
        ms.setWorldScanner(ws);
        ms.setSalienceMap(sm);
        ms.setAffordanceRouter(ar);
        ms.setWorkingMemoryPool(wmp);
        ms.setActionSorter(as);
        assertTrue(ms.isActionSorterPipelineBound());
    }

    @Test @DisplayName("unbinding one component breaks pipeline")
    void oneMissingBreaks() {
        var ms = new MetaScheduler(null);
        ms.setWorldScanner(new WorldScanner());
        ms.setSalienceMap(new SalienceMap(new ValueRegistry(tempDir)));
        ms.setAffordanceRouter(new AffordanceRouter());
        ms.setWorkingMemoryPool(new WorkingMemoryPool());
        // No ActionSorter set
        assertFalse(ms.isActionSorterPipelineBound());
    }
}
