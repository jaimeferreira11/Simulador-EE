package py.simulador.equipo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.bot.BotPersonalityAssigner;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;
import py.simulador.common.BusinessValidationException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.competencia.CompetenciaStateMachine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests for bot-team behaviour in {@link EquipoService}.
 * <p>
 * Verifies that:
 * <ul>
 *   <li>{@code crearEquipoBot} persists an equipo with {@code tipo=BOT}, a difficulty, and an
 *       auto-assigned personality.</li>
 *   <li>Adding a jugador to a bot team is rejected (conflict).</li>
 *   <li>When multiple bots are created in the same competencia the assigner is queried with
 *       the existing personalities so each new bot picks the least-represented one.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EquipoServiceBotTest {

    @Mock EquipoRepository equipoRepo;
    @Mock EquipoMiembroRepository miembroRepo;
    @Mock CompetenciaRepository competenciaRepo;

    private BotPersonalityAssigner assigner;
    private EquipoService service;

    private final List<EquipoEntity> savedEquipos = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        assigner = new BotPersonalityAssigner();
        savedEquipos.clear();
        idSeq.set(1);
        service = new EquipoService(equipoRepo, miembroRepo, competenciaRepo, assigner);
    }

    @Test
    void crear_equipo_bot_persists_with_personality_assigned() {
        Long competenciaId = 10L;
        CompetenciaEntity comp = competenciaBorrador(competenciaId, (short) 8);
        when(competenciaRepo.findById(competenciaId)).thenReturn(Optional.of(comp));
        when(equipoRepo.countByCompetenciaId(competenciaId)).thenReturn(0L);
        when(equipoRepo.findByCompetenciaIdAndTipo(competenciaId, "BOT"))
                .thenReturn(List.of());
        when(equipoRepo.save(any(EquipoEntity.class))).thenAnswer(inv -> {
            EquipoEntity e = inv.getArgument(0);
            e.setId(idSeq.getAndIncrement());
            savedEquipos.add(e);
            return e;
        });

        EquipoEntity result = service.crearEquipoBot(
                competenciaId, "Bot Alfa", "#FF0000", Difficulty.MEDIO);

        assertThat(result.esBot()).isTrue();
        assertThat(result.getTipo()).isEqualTo("BOT");
        assertThat(result.getDificultad()).isEqualTo("MEDIO");
        assertThat(result.getPersonalidad()).isNotNull();
        assertThat(result.getNombreEmpresa()).isEqualTo("Bot Alfa");
        assertThat(result.getCodigoColor()).isEqualTo("#FF0000");
        assertThat(result.getEstado()).isEqualTo("ACTIVO");
        // First bot, no existing personalities -> first enum value.
        assertThat(result.getPersonalidad()).isEqualTo(Personality.COST_LEADER.name());
    }

    @Test
    void cannot_add_jugador_to_bot_team() {
        Long equipoId = 42L;
        EquipoEntity bot = new EquipoEntity();
        bot.setId(equipoId);
        bot.setCompetenciaId(10L);
        bot.setTipo("BOT");
        bot.setDificultad("MEDIO");
        bot.setPersonalidad(Personality.COST_LEADER.name());
        when(equipoRepo.findById(equipoId)).thenReturn(Optional.of(bot));

        assertThatThrownBy(() -> service.addMiembro(equipoId, 99L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("BOT");
    }

    @Test
    void crearEquipoBot_honors_personalidad_override_when_provided() {
        Long competenciaId = 10L;
        CompetenciaEntity comp = competenciaBorrador(competenciaId, (short) 8);
        when(competenciaRepo.findById(competenciaId)).thenReturn(Optional.of(comp));
        when(equipoRepo.countByCompetenciaId(competenciaId)).thenReturn(0L);
        when(equipoRepo.save(any(EquipoEntity.class))).thenAnswer(inv -> {
            EquipoEntity e = inv.getArgument(0);
            e.setId(idSeq.getAndIncrement());
            savedEquipos.add(e);
            return e;
        });

        // First bot would normally get COST_LEADER from the assigner; override forces PREMIUM.
        EquipoEntity result = service.crearEquipoBot(
                competenciaId, "Bot Override", "#123456", Difficulty.MEDIO, Personality.PREMIUM);

        assertThat(result.esBot()).isTrue();
        assertThat(result.getPersonalidad()).isEqualTo(Personality.PREMIUM.name());
        // Assigner should be skipped: no need to query existing bots for personality calculation.
        org.mockito.Mockito.verify(equipoRepo, org.mockito.Mockito.never())
                .findByCompetenciaIdAndTipo(anyLong(), any());
    }

    @Test
    void crearEquipoBot_assigns_distinct_personalities_for_multiple_bots() {
        Long competenciaId = 10L;
        CompetenciaEntity comp = competenciaBorrador(competenciaId, (short) 8);
        when(competenciaRepo.findById(competenciaId)).thenReturn(Optional.of(comp));

        // For each call, return the current count of saved bots and the list of their personalities.
        when(equipoRepo.countByCompetenciaId(competenciaId))
                .thenAnswer(inv -> (long) savedEquipos.size());
        when(equipoRepo.findByCompetenciaIdAndTipo(competenciaId, "BOT"))
                .thenAnswer(inv -> List.copyOf(savedEquipos));
        when(equipoRepo.save(any(EquipoEntity.class))).thenAnswer(inv -> {
            EquipoEntity e = inv.getArgument(0);
            e.setId(idSeq.getAndIncrement());
            savedEquipos.add(e);
            return e;
        });

        EquipoEntity b1 = service.crearEquipoBot(competenciaId, "Bot 1", "#FF0000", Difficulty.FACIL);
        EquipoEntity b2 = service.crearEquipoBot(competenciaId, "Bot 2", "#00FF00", Difficulty.MEDIO);
        EquipoEntity b3 = service.crearEquipoBot(competenciaId, "Bot 3", "#0000FF", Difficulty.DIFICIL);

        Set<String> personalidades = new HashSet<>();
        personalidades.add(b1.getPersonalidad());
        personalidades.add(b2.getPersonalidad());
        personalidades.add(b3.getPersonalidad());

        // All three Personality values were used exactly once -> no duplicates.
        assertThat(personalidades).hasSize(3);
        assertThat(personalidades).containsExactlyInAnyOrder(
                Personality.COST_LEADER.name(),
                Personality.PREMIUM.name(),
                Personality.BALANCEADO.name());
    }

    private CompetenciaEntity competenciaBorrador(Long id, short maxEquipos) {
        CompetenciaEntity comp = new CompetenciaEntity();
        comp.setId(id);
        comp.setEstado(CompetenciaStateMachine.BORRADOR);
        comp.setNumEquiposMax(maxEquipos);
        return comp;
    }
}
