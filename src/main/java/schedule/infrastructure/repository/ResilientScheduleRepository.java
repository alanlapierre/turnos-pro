package schedule.infrastructure.repository;

import schedule.domain.models.Schedule;
import schedule.domain.repository.ScheduleRepository;
import schedule.infrastructure.resilience.ResilientExecutor;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public class ResilientScheduleRepository implements ScheduleRepository {

    private final ScheduleRepository next; // El repositorio JDBC puro real
    private final ResilientExecutor resilientExecutor;

    public ResilientScheduleRepository(ScheduleRepository next, ResilientExecutor resilientExecutor) {
        this.next = next;
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public Optional<Schedule> findById(UUID id) {
        return next.findById(id); // La lectura simple no requiere reintento
    }

    @Override
    public void update(Schedule schedule) {
        // Mantenemos la firma obligatoria por contrato de interfaz
        next.update(schedule);
    }

    /**
     * Este es el nuevo método extendido del decorador para Casos de Uso Concurrentes de Alta Performance.
     * Recibe el ID de la agenda y una FUNCIÓN con la lógica de negocio que altera el dominio.
     */
    public Schedule updateResiliently(UUID id, UnaryOperator<Schedule> domainBusinessLogic) {
        return resilientExecutor.executeWithRetry(() -> {

            // STEP 1: Recarga fresca desde PostgreSQL (Asegura tener la última versión en disco)
            Schedule freshSchedule = next.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));

            // STEP 2: Re-ejecución de la lógica de negocio sobre el nuevo estado en memoria
            // Ej: Aquí adentro se chequeará si el slot sigue libre en esta nueva versión
            Schedule mutatedSchedule = domainBusinessLogic.apply(freshSchedule);

            // STEP 3: Persistencia atómica. Si otra transacción mutó el registro,
            // rowsUpdated será 0 y el JdbcScheduleRepository lanzará ConcurrentModificationException
            next.update(mutatedSchedule);

            return mutatedSchedule;
        });
    }
}
