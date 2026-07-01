package py.simulador.resultado;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio de consulta de resultados, snapshots y rankings.
 * Solo lectura — los datos se persisten desde MotorSimulacion.
 */
@Service
public class ResultadoService {

    private final ResultadoCalculoRepository resultadoRepo;
    private final SnapshotEstadoRepository snapshotRepo;
    private final RankingTrimestreRepository rankingRepo;
    private final EquipoRepository equipoRepo;
    private final TrimestreRepository trimestreRepo;

    public ResultadoService(ResultadoCalculoRepository resultadoRepo,
                            SnapshotEstadoRepository snapshotRepo,
                            RankingTrimestreRepository rankingRepo,
                            EquipoRepository equipoRepo,
                            TrimestreRepository trimestreRepo) {
        this.resultadoRepo = resultadoRepo;
        this.snapshotRepo = snapshotRepo;
        this.rankingRepo = rankingRepo;
        this.equipoRepo = equipoRepo;
        this.trimestreRepo = trimestreRepo;
    }

    @Transactional(readOnly = true)
    public List<ResultadoCalculoEntity> findResultadosByTrimestre(Long trimestreId) {
        return resultadoRepo.findByTrimestreId(trimestreId);
    }

    @Transactional(readOnly = true)
    public ResultadoCalculoEntity findResultado(Long equipoId, Long trimestreId) {
        return resultadoRepo.findByEquipoIdAndTrimestreId(equipoId, trimestreId)
                .orElseThrow(() -> new ResourceNotFoundException("ResultadoCalculo",
                        "equipo_trimestre", equipoId + "/" + trimestreId));
    }

    @Transactional(readOnly = true)
    public SnapshotEstadoEntity findSnapshot(Long equipoId, Long trimestreId, String momento) {
        return snapshotRepo.findByEquipoIdAndTrimestreIdAndMomento(equipoId, trimestreId, momento)
                .orElseThrow(() -> new ResourceNotFoundException("SnapshotEstado",
                        "equipo_trimestre_momento", equipoId + "/" + trimestreId + "/" + momento));
    }

    /**
     * Ranking de un trimestre específico. Si no se indica trimestreId,
     * usa el último trimestre procesado de la competencia.
     */
    @Transactional(readOnly = true)
    public List<RankingTrimestreEntity> findRanking(Long competenciaId, Long trimestreId) {
        Long triId = trimestreId;
        if (triId == null) {
            triId = trimestreRepo.findUltimoTrimestreProcesado(competenciaId)
                    .map(TrimestreEntity::getId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Trimestre procesado", "competencia", String.valueOf(competenciaId)));
        }
        return rankingRepo.findByCompetenciaIdAndTrimestreId(competenciaId, triId);
    }

    /** Mapa equipoId → EquipoEntity para enriquecer ranking con nombre/color */
    @Transactional(readOnly = true)
    public Map<Long, EquipoEntity> equiposPorCompetencia(Long competenciaId) {
        return equipoRepo.findByCompetenciaId(competenciaId).stream()
                .collect(Collectors.toMap(EquipoEntity::getId, e -> e));
    }

    /**
     * Evolución PIP: para cada equipo de la competencia, serie de (trimestre, pipAcumulado).
     */
    @Transactional(readOnly = true)
    public List<RankingTrimestreEntity> findAllRankings(Long competenciaId) {
        return rankingRepo.findByCompetenciaId(competenciaId);
    }

    /** Mapa trimestreId → numero para resolver el Q de cada ranking */
    @Transactional(readOnly = true)
    public Map<Long, Short> trimestreNumeros(Long competenciaId) {
        return trimestreRepo.findByCompetenciaId(competenciaId).stream()
                .collect(Collectors.toMap(TrimestreEntity::getId, TrimestreEntity::getNumero));
    }
}
