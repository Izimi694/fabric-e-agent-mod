package com.izimi.eagent.api;

import com.izimi.eagent.amygdala.FamiliarityTracker;
import com.izimi.eagent.amygdala.SocialObserver;

public interface AmygdalaAPI {
    SocialObserver socialObserver();
    FamiliarityTracker familiarityTracker();
}
