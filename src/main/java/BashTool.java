import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class BashTool {
    private static final String NAME = "Bash";
    private static final String DESCRIPTION = "Execute a shell command";
    private static final String COMMAND = "command";
    private static final String COMMAND_DESCRIPTION = "The command to execute";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ChatCompletionTool TOOL = ChatCompletionTool.builder()
            .function(FunctionDefinition.builder()
                    .name(NAME)
                    .description(DESCRIPTION)
                    .parameters(parameters())
                    .build())
            .build();

    private BashTool() {}

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
            JsonNode commandNode = arguments.get(COMMAND);
            if (commandNode == null || !commandNode.isTextual()) {
                throw new IllegalArgumentException("missing or invalid command argument");
            }

            String command = commandNode.asText();
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", command);
            builder.directory(new File(System.getProperty("user.dir")));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return output;
            }
            return "Command exited with code " + exitCode + ":\n" + output;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("failed to parse Bash tool arguments", e);
        } catch (IOException e) {
            throw new RuntimeException("failed to execute command", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("command interrupted", e);
        }
    }

    private static FunctionParameters parameters() {
        return FunctionParameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        COMMAND, Map.of(
                                "type", "string",
                                "description", COMMAND_DESCRIPTION
                        )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of(COMMAND)))
                .build();
    }
}
