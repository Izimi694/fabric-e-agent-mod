package com.izimi.eagent.api;

import com.izimi.eagent.brainstem.adapter.BasicActionAdapter;
import com.izimi.eagent.brainstem.domain.DomainRouter;
import com.izimi.eagent.brainstem.innate.InnateReflexRegistry;
import com.izimi.eagent.brainstem.scheduler.InhibitoryControl;

public interface BrainstemAPI {
    InnateReflexRegistry innateReflexes();
    BasicActionAdapter basicActions();
    DomainRouter domainRouter();
    InhibitoryControl inhibitor();
}
