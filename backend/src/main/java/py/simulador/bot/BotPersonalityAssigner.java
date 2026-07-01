package py.simulador.bot;

import org.springframework.stereotype.Component;
import py.simulador.bot.model.Personality;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class BotPersonalityAssigner {

    /**
     * Elige la personalidad menos representada entre los bots existentes.
     * En caso de empate, devuelve la primera del enum según declaration order.
     */
    public Personality assignFor(List<Personality> existing) {
        Map<Personality, Integer> counts = new EnumMap<>(Personality.class);
        for (Personality p : Personality.values()) {
            counts.put(p, 0);
        }
        for (Personality p : existing) {
            counts.merge(p, 1, Integer::sum);
        }

        Personality leastUsed = Personality.values()[0];
        int minCount = counts.get(leastUsed);
        for (Personality p : Personality.values()) {
            if (counts.get(p) < minCount) {
                minCount = counts.get(p);
                leastUsed = p;
            }
        }
        return leastUsed;
    }
}
