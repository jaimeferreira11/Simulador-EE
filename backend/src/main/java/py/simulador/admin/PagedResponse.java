package py.simulador.admin;

import java.util.List;

/**
 * Respuesta paginada genérica para ABMs admin.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PagedResponse<T> of(List<T> allItems, int page, int size) {
        int total = allItems.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        return new PagedResponse<>(allItems.subList(from, to), page, size, total, totalPages);
    }
}
