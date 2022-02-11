package com.sequenceiq.datalake.flow.freeipa.upscale;

import com.sequenceiq.datalake.flow.FillInMemoryStateStoreRestartAction;
import com.sequenceiq.flow.core.FlowState;
import com.sequenceiq.flow.core.RestartAction;
import com.sequenceiq.flow.core.restart.DefaultRestartAction;

public enum FreeIpaUpscaleState implements FlowState {

    INIT_STATE,
    FREEIPA_UPSCALE_START_STATE,
    FREEIPA_UPSCALE_IN_PROGRESS_STATE,
    FREEIPA_UPSCALE_FINISHED_STATE,
    FREEIPA_UPSCALE_FAILED_STATE,
    FINAL_STATE;

    private Class<? extends DefaultRestartAction> restartAction = FillInMemoryStateStoreRestartAction.class;

    FreeIpaUpscaleState() {
    }

    FreeIpaUpscaleState(Class<? extends DefaultRestartAction> restartAction) {
        this.restartAction = restartAction;
    }

    @Override
    public Class<? extends RestartAction> restartAction() {
        return restartAction;
    }

}
