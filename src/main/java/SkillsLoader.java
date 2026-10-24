import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SkillsLoader {
    private static final Path SKILLS_DIR = Path.of(".claude", "skills");

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
     * If the prompt starts with /skill-name, return that skill's SKILL.md body (no frontmatter).
     */
    public static Optional<String> invokedSkillBody(String prompt) {
        String skillName = parseSlashSkillName(prompt);
        if (skillName == null) {
            return Optional.empty();
        }
        return loadSkillBody(skillName);
    }

    private static String parseSlashSkillName(String prompt) {
        String trimmed = prompt.trim();
        if (!trimmed.startsWith("/")) {
            return null;
        }
        String rest = trimmed.substring(1).trim();
        if (rest.isEmpty()) {
            return null;
        }
        int space = rest.indexOf(' ');
        return space < 0 ? rest : rest.substring(0, space);
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

    private record Skill(String name, String description) {}
}
