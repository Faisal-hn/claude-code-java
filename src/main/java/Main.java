import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2 || !"-p".equals(args[0])) {
            System.err.println("Usage: program -p <prompt>");
            System.exit(1);
        }

        String prompt = args[1];

        String apiKey = System.getenv("OPENROUTER_API_KEY");
        String baseUrl = System.getenv("OPENROUTER_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://openrouter.ai/api/v1";
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("OPENROUTER_API_KEY is not set");
        }

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.err.println("Logs from your program will appear here!");

        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                .model("anthropic/claude-haiku-4.5")
                .addUserMessage(prompt)
                .addTool(ReadTool.tool())
                .addTool(WriteTool.tool());

        while (true) {
            ChatCompletion response = client.chat().completions().create(params.build());

            if (response.choices().isEmpty()) {
                throw new RuntimeException("no choices in response");
            }

            ChatCompletionMessage message = response.choices().get(0).message();
            params.addMessage(message);

            List<ChatCompletionMessageToolCall> toolCalls = message.toolCalls().orElse(List.of());
            if (toolCalls.isEmpty()) {
                System.out.print(message.content().orElse(""));
                break;
            }

            for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                String name = toolCall.function().name();
                String result = switch (name) {
                    case "Read" -> ReadTool.execute(toolCall);
                    case "Write" -> WriteTool.execute(toolCall);
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
