package py.simulador.invitacion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import py.simulador.auth.RolCache;
import py.simulador.common.BusinessValidationException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.email.EmailService;
import py.simulador.notificacion.NotificacionService;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoMiembroEntity;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.equipo.EquipoRepository;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitacionServiceTest {

    @Mock InvitacionRepository invitacionRepo;
    @Mock EquipoRepository equipoRepo;
    @Mock CompetenciaRepository competenciaRepo;
    @Mock EquipoMiembroRepository miembroRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RolCache rolCache;
    @Mock EmailService emailService;
    @Mock NotificacionService notificacionService;

    private InvitacionService service;

    @BeforeEach
    void setUp() {
        service = new InvitacionService(invitacionRepo, equipoRepo, competenciaRepo,
                miembroRepo, usuarioRepo, passwordEncoder, rolCache, emailService, notificacionService);
    }

    @Test
    void invitarCreaInvitacion() {
        EquipoEntity equipo = new EquipoEntity();
        equipo.setId(1L);
        equipo.setCompetenciaId(10L);
        when(equipoRepo.findById(1L)).thenReturn(Optional.of(equipo));
        when(competenciaRepo.findById(10L)).thenReturn(Optional.of(crearCompetencia(10L, null)));
        when(invitacionRepo.findPendienteByEquipoIdAndEmail(1L, "jugador@test.com"))
                .thenReturn(Optional.empty());
        when(usuarioRepo.findByEmail("jugador@test.com")).thenReturn(Optional.empty());
        when(invitacionRepo.save(any())).thenAnswer(inv -> {
            InvitacionEntity e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });

        InvitacionEntity result = service.invitar(1L, "jugador@test.com", "Juan Perez", null, false, 5L);

        assertNotNull(result.getToken());
        assertEquals("PENDIENTE", result.getEstado());
        assertEquals("jugador@test.com", result.getEmail());
        assertEquals(5L, result.getCreadaPor());
    }

    @Test
    void invitarRetornaExistenteSiYaPendiente() {
        EquipoEntity equipo = new EquipoEntity();
        equipo.setId(1L);
        equipo.setCompetenciaId(10L);
        when(equipoRepo.findById(1L)).thenReturn(Optional.of(equipo));
        when(competenciaRepo.findById(10L)).thenReturn(Optional.of(crearCompetencia(10L, null)));

        InvitacionEntity existente = new InvitacionEntity();
        existente.setId(99L);
        existente.setEstado("PENDIENTE");
        when(invitacionRepo.findPendienteByEquipoIdAndEmail(1L, "jugador@test.com"))
                .thenReturn(Optional.of(existente));

        InvitacionEntity result = service.invitar(1L, "jugador@test.com", "Juan", null, false, 5L);
        assertEquals(99L, result.getId());
        verify(invitacionRepo, never()).save(any());
    }

    @Test
    void aceptarCreaUsuarioYAgregaAEquipo() {
        InvitacionEntity inv = crearInvitacionPendiente();
        when(invitacionRepo.findByToken("test-token")).thenReturn(Optional.of(inv));
        when(usuarioRepo.findByEmail("nuevo@test.com")).thenReturn(Optional.empty());
        when(rolCache.getId("JUGADOR")).thenReturn(3L);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(usuarioRepo.save(any())).thenAnswer(i -> {
            UsuarioEntity u = i.getArgument(0);
            u.setId(50L);
            return u;
        });
        when(miembroRepo.findByEquipoIdAndUsuarioId(1L, 50L)).thenReturn(Optional.empty());
        when(miembroRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(invitacionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        UsuarioEntity result = service.aceptar("test-token", "password123");

        assertEquals("nuevo@test.com", result.getEmail());
        assertTrue(result.isEmailVerificado());
        verify(miembroRepo).save(argThat(m -> m.getEquipoId() == 1L && !m.isEsCapitan()));
    }

    @Test
    void aceptarConUsuarioExistenteAgregaAEquipo() {
        InvitacionEntity inv = crearInvitacionPendiente();
        when(invitacionRepo.findByToken("test-token")).thenReturn(Optional.of(inv));

        UsuarioEntity existente = new UsuarioEntity();
        existente.setId(20L);
        existente.setEmail("nuevo@test.com");
        existente.setActivo(true);
        when(usuarioRepo.findByEmail("nuevo@test.com")).thenReturn(Optional.of(existente));
        when(miembroRepo.findByEquipoIdAndUsuarioId(1L, 20L)).thenReturn(Optional.empty());
        when(miembroRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(invitacionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        UsuarioEntity result = service.aceptar("test-token", "password123");
        assertEquals(20L, result.getId());
    }

    @Test
    void aceptarInvitacionExpiradaFalla() {
        InvitacionEntity inv = crearInvitacionPendiente();
        inv.setExpiresAt(OffsetDateTime.now().minusDays(1));
        when(invitacionRepo.findByToken("test-token")).thenReturn(Optional.of(inv));
        when(invitacionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThrows(BusinessValidationException.class,
                () -> service.aceptar("test-token", "password123"));
    }

    @Test
    void aceptarInvitacionYaAceptadaFalla() {
        InvitacionEntity inv = crearInvitacionPendiente();
        inv.setEstado("ACEPTADA");
        when(invitacionRepo.findByToken("test-token")).thenReturn(Optional.of(inv));

        assertThrows(BusinessValidationException.class,
                () -> service.aceptar("test-token", "password123"));
    }

    @Test
    void invitarRespetaLimiteDeIntegrantes() {
        EquipoEntity equipo = new EquipoEntity();
        equipo.setId(1L);
        equipo.setCompetenciaId(10L);
        when(equipoRepo.findById(1L)).thenReturn(Optional.of(equipo));
        // Límite = 2: ya hay 1 miembro y 1 invitación pendiente => no caben más
        when(competenciaRepo.findById(10L)).thenReturn(Optional.of(crearCompetencia(10L, (short) 2)));
        when(invitacionRepo.findPendienteByEquipoIdAndEmail(1L, "nuevo@test.com"))
                .thenReturn(Optional.empty());
        when(miembroRepo.findByEquipoId(1L)).thenReturn(List.of(new EquipoMiembroEntity()));
        when(invitacionRepo.countPendientesByEquipoId(1L)).thenReturn(1L);

        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> service.invitar(1L, "nuevo@test.com", "Nuevo", null, false, 5L));
        assertTrue(ex.getMessage().contains("máximo de 2"));
        verify(invitacionRepo, never()).save(any());
    }

    @Test
    void invitarConCupoDisponibleCrea() {
        EquipoEntity equipo = new EquipoEntity();
        equipo.setId(1L);
        equipo.setCompetenciaId(10L);
        when(equipoRepo.findById(1L)).thenReturn(Optional.of(equipo));
        // Límite = 3: 1 miembro + 1 pendiente + esta = 3 (cabe justo)
        when(competenciaRepo.findById(10L)).thenReturn(Optional.of(crearCompetencia(10L, (short) 3)));
        when(invitacionRepo.findPendienteByEquipoIdAndEmail(1L, "nuevo@test.com"))
                .thenReturn(Optional.empty());
        when(miembroRepo.findByEquipoId(1L)).thenReturn(List.of(new EquipoMiembroEntity()));
        when(invitacionRepo.countPendientesByEquipoId(1L)).thenReturn(1L);
        when(usuarioRepo.findByEmail("nuevo@test.com")).thenReturn(Optional.empty());
        when(invitacionRepo.save(any())).thenAnswer(i -> {
            InvitacionEntity e = i.getArgument(0);
            e.setId(200L);
            return e;
        });

        InvitacionEntity result = service.invitar(1L, "nuevo@test.com", "Nuevo", null, false, 5L);
        assertEquals("PENDIENTE", result.getEstado());
    }

    private CompetenciaEntity crearCompetencia(Long id, Short maxIntegrantes) {
        CompetenciaEntity c = new CompetenciaEntity();
        c.setId(id);
        c.setNombre("Competencia Test");
        c.setMaxIntegrantesEquipo(maxIntegrantes);
        return c;
    }

    private InvitacionEntity crearInvitacionPendiente() {
        InvitacionEntity inv = new InvitacionEntity();
        inv.setId(1L);
        inv.setEquipoId(1L);
        inv.setEmail("nuevo@test.com");
        inv.setNombreCompleto("Nuevo Jugador");
        inv.setToken("test-token");
        inv.setEstado("PENDIENTE");
        inv.setExpiresAt(OffsetDateTime.now().plusDays(7));
        return inv;
    }
}
