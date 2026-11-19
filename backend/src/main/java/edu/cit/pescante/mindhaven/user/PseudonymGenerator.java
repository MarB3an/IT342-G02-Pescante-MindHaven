package edu.cit.pescante.mindhaven.user;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PseudonymGenerator {

    private static final List<String> ADJECTIVES = List.of(
            "Quiet", "Gentle", "Calm", "Kind", "Warm", "Soft", "Still",
            "Peaceful", "Steady", "Bright", "Serene", "Hopeful", "Mellow", "Clear"
    );

    private static final List<String> NOUNS = List.of(
            "Sparrow", "Willow", "Harbor", "Meadow", "Breeze", "Lotus", "River",
            "Cedar", "Lantern", "Pebble", "Horizon", "Cloud", "Robin", "Orchard"
    );

    public static final Set<String> VALID_AVATARS = Set.of(
            "sparrow", "willow", "harbor", "lotus", "breeze", "lantern", "meadow", "aurora"
    );

    private static final List<String> BLOCKED_TERMS = List.of(
            "admin", "counselor", "staff", "moderator", "fuck", "shit", "bitch", "asshole", "bastard"
    );

    private static final Pattern PII_PATTERN = Pattern.compile(".*(\\d{6,}|@|\\.edu|\\.com).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern HANDLE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{3,29}$");

    private final SecureRandom random = new SecureRandom();
    private final StudentProfileRepository studentProfileRepository;

    public PseudonymGenerator(StudentProfileRepository studentProfileRepository) {
        this.studentProfileRepository = studentProfileRepository;
    }

    public String generateUniqueHandle() {
        for (int attempt = 0; attempt < 25; attempt++) {
            String adj = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
            String noun = NOUNS.get(random.nextInt(NOUNS.size()));
            int number = 10 + random.nextInt(90);
            String candidate = adj + noun + number;
            if (!studentProfileRepository.existsByPseudonymHandleIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return "QuietSparrow" + (1000 + random.nextInt(9000));
    }

    public String pickDefaultAvatar() {
        List<String> list = List.copyOf(VALID_AVATARS);
        return list.get(random.nextInt(list.size()));
    }

    public String validateCustomHandle(String rawHandle) {
        if (rawHandle == null || rawHandle.isBlank()) {
            return "Pseudonym handle is required.";
        }
        String trimmed = rawHandle.trim();
        if (!HANDLE_PATTERN.matcher(trimmed).matches()) {
            return "Pseudonym must be 4-30 characters, start with a letter, and contain only letters, digits, hyphens, or underscores.";
        }
        String lower = trimmed.toLowerCase();
        for (String blocked : BLOCKED_TERMS) {
            if (lower.contains(blocked)) {
                return "Pseudonym contains a reserved or prohibited word.";
            }
        }
        if (PII_PATTERN.matcher(trimmed).matches()) {
            return "Pseudonym cannot contain email patterns or long numeric IDs to protect your privacy.";
        }
        return null;
    }
}
