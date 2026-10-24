import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ReadTool {
    private static final String NAME = "Read";
    private static final String DESCRIPTION = "Read and return the contents of a file";
    private static final String FILE_PATH = "file_path";
    private static final String FILE_PATH_DESCRIPTION = "The path to the file to read";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    public static String execute(ChatCompletionMessageToolCall toolCall) {
        ChatCompletionMessageToolCall.Function function = toolCall.function();
        if (!NAME.equals(function.name())) {
            throw new IllegalArgumentException("unsupported tool: " + function.name());
        }

        try {
            JsonNode arguments = OBJECT_MAPPER.readTree(function.arguments());
            JsonNode filePathNode = arguments.get(FILE_PATH);
            if (filePathNode == null || !filePathNode.isTextual()) {
                throw new IllegalArgumentException("missing or invalid file_path argument");
            }
            return Files.readString(Path.of(filePathNode.asText()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("failed to parse Read tool arguments", e);
        } catch (IOException e) {
            throw new RuntimeException("failed to read file", e);
        }
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
