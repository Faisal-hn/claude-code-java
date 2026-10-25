import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

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
                .model("anthropic/claude-haiku-4.5");

        String skillsPrompt = SkillsLoader.systemPrompt();
        if (!skillsPrompt.isEmpty()) {
            params.addSystemMessage(skillsPrompt);
        }

        List<String> skillBodies = SkillsLoader.invokedSkillBodies(prompt);
        if (skillBodies.isEmpty()) {
            params.addUserMessage(prompt);
        } else {
            for (String body : skillBodies) {
                params.addUserMessage(body);
            }
        }

        params.addTool(ReadTool.tool())
                .addTool(WriteTool.tool())
                .addTool(BashTool.tool())
                .addTool(SkillTool.tool());

        System.out.print(AgentLoop.run(client, params));
    }
}
