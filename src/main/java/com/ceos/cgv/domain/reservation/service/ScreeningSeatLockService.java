package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScreeningSeatLockService {
    private final ScreeningSeatRepository screeningSeatRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ScreeningSeat> lockSeats(Long screeningId, Collection<SeatCoordinate> coordinates) {
        if (coordinates.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<SeatCoordinate> sorted = new ArrayList<>(coordinates);
        sorted.sort(Comparator.comparing(SeatCoordinate::row).thenComparingInt(SeatCoordinate::number));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).equals(sorted.get(i))) {
                throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
            }
        }

        List<ScreeningSeat> locked = new ArrayList<>(sorted.size());
        for (SeatCoordinate coordinate : sorted) {
            try {
                locked.add(screeningSeatRepository.lockCoordinateNowait(
                                screeningId, coordinate.row(), coordinate.number())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY)));
            } catch (PessimisticLockingFailureException | LockTimeoutException
                     | PessimisticLockException exception) {
                throw new BusinessException(ErrorCode.SEAT_BUSY);
            } catch (DataAccessException | PersistenceException exception) {
                if (isMysqlNowait(exception)) {
                    throw new BusinessException(ErrorCode.SEAT_BUSY);
                }
                throw exception;
            }
        }
        return locked;
    }

    private static boolean isMysqlNowait(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && sql.getErrorCode() == 3572 && "HY000".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
