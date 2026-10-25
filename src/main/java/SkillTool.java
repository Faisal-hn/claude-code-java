import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SkillTool {
    private static final String NAME = "Skill";
    private static final String DESCRIPTION = "Load a skill's instructions into the conversation";
    private static final String SKILL_NAME = "name";
    private static final String SKILL_NAME_DESCRIPTION = "The name of the skill to use";
    private static final String ARGS = "args";
    private static final String ARGS_DESCRIPTION = "Optional arguments for the skill";
    private static final String MODEL = "anthropic/claude-haiku-4.5";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ChatCompletionTool TOOL = ChatCompletionTool.builder()
            .function(FunctionDefinition.builder()
                    .name(NAME)
                    .description(DESCRIPTION)
                    .parameters(parameters())
                    .build())
            .build();

    private SkillTool() {}

    public static ChatCompletionTool tool() {
        return TOOL;
    }

    public static String execute(ChatCompletionMessageToolCall toolCall, OpenAIClient client) {
        ChatCompletionMessageToolCall.Function function = toolCall.function();
        if (!NAME.equals(function.name())) {
            throw new IllegalArgumentException("unsupported tool: " + function.name());
        }

        try {
            JsonNode arguments = OBJECT_MAPPER.readTree(function.arguments());
            JsonNode nameNode = arguments.get(SKILL_NAME);
            if (nameNode == null || !nameNode.isTextual()) {
                return "Error: missing or invalid name argument";
            }

            String skillName = nameNode.asText();
            JsonNode argsNode = arguments.get(ARGS);
            String args = argsNode != null && argsNode.isTextual() ? argsNode.asText() : "";

            if (SkillsLoader.isFork(skillName)) {
                return runInSubagent(client, skillName, args);
            }
            return SkillsLoader.loadSkillContent(skillName, args);
        } catch (JsonProcessingException e) {
            return "Error: failed to parse Skill tool arguments";
        }
    }

    private static String runInSubagent(OpenAIClient client, String skillName, String args) {
        Optional<String> body = SkillsLoader.loadSubstitutedBody(skillName, args);
        if (body.isEmpty()) {
            return "Error: skill not found: " + skillName;
        }

        ChatCompletionCreateParams.Builder subParams = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addUserMessage(body.get())
                .addTool(ReadTool.tool())
                .addTool(WriteTool.tool())
                .addTool(BashTool.tool())
                .addTool(SkillTool.tool());

        String answer = AgentLoop.run(client, subParams).trim();
        return "Skill " + skillName + " ran in a separate context and returned: " + answer;
    }

    private static FunctionParameters parameters() {
        return FunctionParameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        SKILL_NAME, Map.of(
                                "type", "string",
                                "description", SKILL_NAME_DESCRIPTION
                        ),
                        ARGS, Map.of(
                                "type", "string",
                                "description", ARGS_DESCRIPTION
                        )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of(SKILL_NAME)))
                .build();
    }
}
