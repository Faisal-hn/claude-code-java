import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;

import java.util.List;

public final class AgentLoop {
    private AgentLoop() {}

    public static String run(OpenAIClient client, ChatCompletionCreateParams.Builder params) {
        while (true) {
            ChatCompletion response = client.chat().completions().create(params.build());

            if (response.choices().isEmpty()) {
                throw new RuntimeException("no choices in response");
            }

            ChatCompletionMessage message = response.choices().get(0).message();
            params.addMessage(message);

            List<ChatCompletionMessageToolCall> toolCalls = message.toolCalls().orElse(List.of());
            if (toolCalls.isEmpty()) {
                return message.content().orElse("");
            }

            for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                String name = toolCall.function().name();
                String result = switch (name) {
                    case "Read" -> ReadTool.execute(toolCall);
                    case "Write" -> WriteTool.execute(toolCall);
                    case "Bash" -> BashTool.execute(toolCall);
                    case "Skill" -> SkillTool.execute(toolCall, client);
                    default -> throw new IllegalArgumentException("unsupported tool: " + name);
                };
                params.addMessage(ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCall.id())
                        .content(result)
                        .build());
            }
        }
    }
}
