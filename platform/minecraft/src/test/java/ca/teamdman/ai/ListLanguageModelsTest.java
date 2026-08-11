package ca.teamdman.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ListLanguageModelsTest {
    @Test
    public void listLocalModels() throws IOException {
        // disabled because this shit breaks on java 20; mc1.21.1
//        String models = new SFMAIModelList().fetchModels();
//        System.out.println(models);
    }
}
