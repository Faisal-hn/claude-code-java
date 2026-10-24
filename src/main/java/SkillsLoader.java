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
     * Expand consecutive leading /skill tokens into substituted bodies that share the trailing argument text.
     */
    public static List<String> invokedSkillBodies(String prompt) {
        StackedInvocation invocation = parseStackedInvocation(prompt);
        if (invocation.skillNames().isEmpty()) {
            return List.of();
        }

        List<String> bodies = new ArrayList<>();
        for (String skillName : invocation.skillNames()) {
            Optional<String> body = loadSkillBody(skillName);
            if (body.isEmpty()) {
                continue;
            }
            String substituted = substituteArguments(body.get(), invocation.arguments(), invocation.positional());
            bodies.add(withSkillLocation(skillName, substituted));
        }
        return bodies;
    }

    private static String withSkillLocation(String skillName, String body) {
        return "Skill: " + skillName
                + " (located at .claude/skills/" + skillName + ")\n"
                + "Paths in the instructions below are relative to that folder.\n\n"
                + body;
    }

    private static StackedInvocation parseStackedInvocation(String prompt) {
        String trimmed = prompt.trim();
        if (trimmed.isEmpty()) {
            return new StackedInvocation(List.of(), "", List.of());
        }

        String[] tokens = trimmed.split("\\s+");
        List<String> skillNames = new ArrayList<>();
        int index = 0;
        while (index < tokens.length) {
            String token = tokens[index];
            if (!token.startsWith("/") || token.length() == 1) {
                break;
            }
            String skillName = token.substring(1);
            if (!skillExists(skillName)) {
                break;
            }
            skillNames.add(skillName);
            index++;
        }

        String arguments = index >= tokens.length
                ? ""
                : String.join(" ", Arrays.copyOfRange(tokens, index, tokens.length));
        List<String> positional = arguments.isEmpty()
                ? List.of()
                : Arrays.asList(arguments.split("\\s+"));
        return new StackedInvocation(skillNames, arguments, positional);
    }

    private static boolean skillExists(String skillName) {
        Path skillMd = Path.of(System.getProperty("user.dir"))
                .resolve(SKILLS_DIR)
                .resolve(skillName)
                .resolve("SKILL.md");
        return Files.isRegularFile(skillMd);
    }

    private static String substituteArguments(String body, String arguments, List<String> positional) {
        String result = replaceAll(ARGUMENTS_INDEX, body, matcher -> {
            int index = Integer.parseInt(matcher.group(1));
            return positionalArg(positional, index);
        });
        result = result.replace("$ARGUMENTS", arguments);
        return replaceAll(POSITIONAL, result, matcher -> {
            int index = Integer.parseInt(matcher.group(1));
            return positionalArg(positional, index);
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

    private record StackedInvocation(List<String> skillNames, String arguments, List<String> positional) {}

    private record Skill(String name, String description) {}
}
