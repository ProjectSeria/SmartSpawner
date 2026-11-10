package github.nighter.smartspawner.api.text;

/**
 * Rewrites a language string after SmartSpawner has substituted its {@code {placeholders}} and before the
 * legacy colour codes ({@code &a}, {@code &#RRGGBB}) are translated.
 * <p>
 * Every player-visible string (GUI titles, item names, lore, chat messages) goes through this hook, so an
 * addon can expand its own tags in language files without touching them.
 * <p>
 * Contract:
 * <ul>
 *   <li>The result must use legacy colour codes ({@code &} or section sign, {@code &#RRGGBB}). SmartSpawner
 *       does not parse MiniMessage.</li>
 *   <li>Return the input unchanged when there is nothing to do. Do this cheaply: the method runs for every
 *       string that is not already cached.</li>
 *   <li>The result is cached together with the input, so it must not depend on the player or on time.</li>
 *   <li>May be called from any thread. Never throw: a thrown exception is logged and the unmodified text is
 *       used.</li>
 * </ul>
 */
@FunctionalInterface
public interface TextPostProcessor {

    /**
     * @param text the string with placeholders already substituted, never null
     * @return the rewritten string, or {@code text} itself when unchanged
     */
    String process(String text);
}
