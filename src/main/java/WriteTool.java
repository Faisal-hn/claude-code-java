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

public final class WriteTool {
    private static final String NAME = "Write";
    private static final String DESCRIPTION = "Write content to a file";
    private static final String FILE_PATH = "file_path";
    private static final String FILE_PATH_DESCRIPTION = "The path of the file to write to";
    private static final String CONTENT = "content";
    private static final String CONTENT_DESCRIPTION = "The content to write to the file";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ChatCompletionTool TOOL = ChatCompletionTool.builder()
            .function(FunctionDefinition.builder()
                    .name(NAME)
                    .description(DESCRIPTION)
                    .parameters(parameters())
                    .build())
            .build();

    private WriteTool() {}

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
            JsonNode contentNode = arguments.get(CONTENT);
            if (filePathNode == null || !filePathNode.isTextual()) {
                throw new IllegalArgumentException("missing or invalid file_path argument");
            }
            if (contentNode == null || !contentNode.isTextual()) {
                throw new IllegalArgumentException("missing or invalid content argument");
            }

            Path path = Path.of(filePathNode.asText());
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, contentNode.asText());
            return "Wrote to " + path;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("failed to parse Write tool arguments", e);
        } catch (IOException e) {
            throw new RuntimeException("failed to write file", e);
        }
    }

    private static FunctionParameters parameters() {
        return FunctionParameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        FILE_PATH, Map.of(
                                "type", "string",
                                "description", FILE_PATH_DESCRIPTION
                        ),
                        CONTENT, Map.of(
                                "type", "string",
                                "description", CONTENT_DESCRIPTION
                        )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of(FILE_PATH, CONTENT)))
                .build();
    }
}
