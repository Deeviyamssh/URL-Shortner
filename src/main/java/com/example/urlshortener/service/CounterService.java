package com.example.urlshortener.service;

import com.example.urlshortener.CounterRepository;
import com.example.urlshortener.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CounterService {

    private static final Logger log = LoggerFactory.getLogger(CounterService.class);
    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = BASE62_CHARS.length();

    private final CounterRepository counterRepository;
    private final UrlMappingRepository urlMappingRepository;

    public CounterService(CounterRepository counterRepository, UrlMappingRepository urlMappingRepository) {
        this.counterRepository = counterRepository;
        this.urlMappingRepository = urlMappingRepository;
    }

    /**
     * Generates a unique short code using a database sequence and Base62 encoding.
     *
     * This method demonstrates several key concepts:
     *
     * 1. **Database Sequences for Concurrency Safety**:
     *    - We use a PostgreSQL sequence (`url_short_code_seq`) to generate unique numbers.
     *    - Sequences are guaranteed to produce unique values even under high concurrency,
     *      thanks to PostgreSQL's internal locking and MVCC (Multi-Version Concurrency Control).
     *    - This avoids the application-level race conditions that would occur if we
     *      generated IDs in the application layer (e.g., using a static variable).
     *    - Even if multiple transactions call `nextval()` simultaneously, each will get
     *      a distinct value, ensuring uniqueness without application-level locks.
     *
     * 2. **Base62 Encoding Algorithm**:
     *    - We convert the sequence number to a Base62 string using the character set:
     *      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" (62 characters).
     *    - The encoding process repeatedly divides the number by 62 and uses the remainder
     *      as an index into the character set.
     *    - Example: Converting 12345 to Base62:
     *        12345 / 62 = 199 remainder 17  -> char at index 17 is 'h'
     *        199 / 62 = 3 remainder 13     -> char at index 13 is 'd'
     *        3 / 62 = 0 remainder 3        -> char at index 3 is '3'
     *        Result: "3dh" (read remainders from last to first)
     *    - Base62 is chosen over Base64 because it omits the '+', '/', and '=' characters,
     *      which have special meaning in URLs and would require encoding.
     *    - Compared to Base16 (hex), Base62 produces shorter strings for the same number.
     *    - Compared to Base36 (only lowercase letters and numbers), Base62 includes
     *      uppercase letters, allowing even shorter strings.
     *
     * 3. **Why Not Random Generation?**:
     *    - Random generation (e.g., using UUID or random strings) carries a risk of
     *      collisions, especially as the number of URLs grows.
     *    - The birthday problem shows that the probability of a collision increases
     *      quadratically with the number of generated codes.
     *    - For a 6-character Base62 code, there are 62^6 ≈ 56.8 billion possible codes.
     *      While this seems large, the probability of a collision with n codes is
     *      approximately 1 - e^(-n^2 / (2 * 62^6)).
     *      For n = 1 million, the collision probability is about 0.009% (1 in 11,000).
     *      For n = 10 million, it rises to about 89%.
     *    - To avoid collisions with random generation, we would need to check for
     *      existence and retry, leading to unpredictable latency and potential
     *      infinite loops under high load.
     *    - A counter-based approach guarantees uniqueness and provides predictable
     *      performance (O(1) for sequence retrieval and O(log_{62}(n)) for encoding).
     *
     * 4. **Padding Decision**:
     *    - We do not pad the short code to a fixed length. This allows the codes to
     *      grow naturally as the sequence value increases.
     *    - Fixed-length codes (e.g., always 6 characters) would waste the larger
     *      code space (we would have to skip to 62^6 before needing 7 characters).
     *    - Variable-length keys are more efficient in terms of space utilization.
     *    - However, variable-length keys may cause issues for clients that expect
     *      a fixed length (e.g., for database storage). We mitigate this by
     *      documenting that the length varies.
     *
     * 5. **Uniqueness Guarantee**:
     *    - Although the sequence guarantees unique numbers, we double-check by
     *      verifying that the generated short code does not already exist in the
     *      database. This protects against the unlikely event of sequence recycling
     *      (if we ever reset the sequence) or external modifications to the table.
     *    - In practice, this check will almost always pass quickly, but it adds
     *      a layer of defense.
     */
    public String generateShortCode() {
        // Get the next unique number from the database sequence
        Long sequenceValue = counterRepository.getNextShortCodeSequenceValue();
        log.debug("Generated sequence value: {}", sequenceValue);

        // Convert to Base62
        String shortCode = encodeToBase62(sequenceValue);
        log.debug("Encoded short code: {}", shortCode);

        // Optional: Verify uniqueness (defensive programming)
        // In a high-throughput system, this check might be omitted for performance
        // after gaining confidence in the sequence approach.
        if (urlMappingRepository.existsByShortCode(shortCode)) {
            // This should never happen under normal circumstances.
            // If it does, we could try the next sequence value, but for simplicity
            // we throw an exception. In production, we might want to retry a few times.
            log.warn("Generated short code {} already exists in database", shortCode);
            throw new IllegalStateException("Generated short code already exists: " + shortCode);
        }

        return shortCode;
    }

    /**
     * Encodes a long value to a Base62 string.
     *
     * @param value the non-negative value to encode
     * @return the Base62 encoded string
     */
    private String encodeToBase62(long value) {
        if (value == 0) {
            return String.valueOf(BASE62_CHARS.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        long n = value;
        while (n > 0) {
            int remainder = (int) (n % BASE);
            sb.append(BASE62_CHARS.charAt(remainder));
            n = n / BASE;
        }
        // The remainders are generated in reverse order, so we reverse the string
        return sb.reverse().toString();
    }
}