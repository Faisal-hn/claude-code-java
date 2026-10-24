import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkillsLoader {
    private static final Path SKILLS_DIR = Path.of(".claude", "skills");
    private static final Pattern ARGUMENTS_INDEX = Pattern.compile("\\$ARGUMENTS\\[(\\d+)]");
    private static final Pattern POSITIONAL = Pattern.compile("\\$(\\d+)");

    private SkillsLoader() {}

    public static String systemPrompt() {
        List<Skill> skills = loadSkills();
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder("You have access to the following skills:\n");
        for (Skill skill : skills) {
            prompt.append("\n- ").append(skill.name()).append(": ").append(skill.description());
        }
        return prompt.toString();
    }

    /**
     * If the prompt starts with /skill-name, return that skill's body with argument placeholders substituted.
     */
    public static Optional<String> invokedSkillBody(String prompt) {
        SlashInvocation invocation = parseSlashInvocation(prompt);
        if (invocation == null) {
            return Optional.empty();
        }
        return loadSkillBody(invocation.skillName()).map(body -> substituteArguments(body, invocation));
    }

    private static SlashInvocation parseSlashInvocation(String prompt) {
        String trimmed = prompt.trim();
        if (!trimmed.startsWith("/")) {
            return null;
        }
        String rest = trimmed.substring(1).trim();
        if (rest.isEmpty()) {
            return null;
        }

        int space = rest.indexOf(' ');
        if (space < 0) {
            return new SlashInvocation(rest, "", List.of());
        }

        String skillName = rest.substring(0, space);
        String arguments = rest.substring(space + 1).trim();
        List<String> positional = arguments.isEmpty()
                ? List.of()
                : Arrays.asList(arguments.split("\\s+"));
        return new SlashInvocation(skillName, arguments, positional);
    }

    private static String substituteArguments(String body, SlashInvocation invocation) {
        String result = replaceAll(ARGUMENTS_INDEX, body, matcher -> {
            int index = Integer.parseInt(matcher.group(1));
            return positionalArg(invocation.positional(), index);
        });
        result = result.replace("$ARGUMENTS", invocation.arguments());
        return replaceAll(POSITIONAL, result, matcher -> {
            int index = Integer.parseInt(matcher.group(1));
            return positionalArg(invocation.positional(), index);
        });
    }

    private static String positionalArg(List<String> positional, int index) {
        if (index < 0 || index >= positional.size()) {
            return "";
        }
        return positional.get(index);
    }

    private static String replaceAll(Pattern pattern, String input, java.util.function.Function<Matcher, String> replacer) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(matcher)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static Optional<String> loadSkillBody(String skillName) {
        Path skillMd = Path.of(System.getProperty("user.dir"))
                .resolve(SKILLS_DIR)
                .resolve(skillName)
                .resolve("SKILL.md");
        if (!Files.isRegularFile(skillMd)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(skillMd);
            return Optional.of(extractBody(content));
        } catch (IOException e) {
            throw new RuntimeException("failed to read skill: " + skillMd, e);
        }
    }

    private static String extractBody(String content) {
        if (!content.startsWith("---")) {
            return content.trim();
        }
        int end = content.indexOf("---", 3);
        if (end < 0) {
            return content.trim();
        }
        return content.substring(end + 3).trim();
    }

    private static List<Skill> loadSkills() {
        Path skillsDir = Path.of(System.getProperty("user.dir")).resolve(SKILLS_DIR);
        if (!Files.isDirectory(skillsDir)) {
            return List.of();
        }

        List<Skill> skills = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir)) {
            for (Path skillDir : stream) {
                if (!Files.isDirectory(skillDir)) {
                    continue;
                }
                Path skillMd = skillDir.resolve("SKILL.md");
                if (!Files.isRegularFile(skillMd)) {
                    continue;
                }
                Skill skill = parseSkill(skillDir.getFileName().toString(), skillMd);
                if (skill != null) {
                    skills.add(skill);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to scan skills directory", e);
        }
        return skills;
    }

    private static Skill parseSkill(String folderName, Path skillMd) {
        try {
            String content = Files.readString(skillMd);
            if (!content.startsWith("---")) {
                return null;
            }
            int end = content.indexOf("---", 3);
            if (end < 0) {
                return null;
            }

            String frontmatter = content.substring(3, end).trim();
            Object loaded = new Yaml().load(frontmatter);
            if (!(loaded instanceof Map<?, ?> map)) {
                return null;
            }

            Object descriptionValue = map.get("description");
            if (descriptionValue == null) {
                return null;
            }
            String description = descriptionValue.toString().trim();
            if (description.isEmpty()) {
                return null;
            }
            return new Skill(folderName, description);
        } catch (IOException e) {
            throw new RuntimeException("failed to read skill: " + skillMd, e);
        }
    }

    private record SlashInvocation(String skillName, String arguments, List<String> positional) {}

    private record Skill(String name, String description) {}
}
