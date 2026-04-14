package ca.teamdman.sfm.common.ai;

import java.io.IOException;

public class SFMAIModelList {
    public String fetchModels() throws IOException {
        return String.join("\n", new SFMAIClient().listModels());
    }
}
