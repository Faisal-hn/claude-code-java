import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.util.List;
import java.util.Map;

public final class ReadTool {
    private static final String NAME = "Read";
    private static final String DESCRIPTION = "Read and return the contents of a file";
    private static final String FILE_PATH = "file_path";
    private static final String FILE_PATH_DESCRIPTION = "The path to the file to read";

    private static final ChatCompletionTool TOOL = ChatCompletionTool.builder()
            .function(FunctionDefinition.builder()
                    .name(NAME)
                    .description(DESCRIPTION)
                    .parameters(parameters())
                    .build())
            .build();

    private ReadTool() {}

    public static ChatCompletionTool tool() {
        return TOOL;
    }

    private static FunctionParameters parameters() {
        return FunctionParameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(FILE_PATH, filePathProperty())))
                .putAdditionalProperty("required", JsonValue.from(List.of(FILE_PATH)))
                .build();
    }

    private static Map<String, String> filePathProperty() {
        return Map.of(
                "type", "string",
                "description", FILE_PATH_DESCRIPTION
        );
    }
}
