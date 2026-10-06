package com.ceos24.spring_cgv.domain.movie.service;

import com.ceos24.spring_cgv.domain.movie.dto.request.CinemaCreateRequest;
import com.ceos24.spring_cgv.domain.movie.dto.request.CinemaUpdateRequest;
import com.ceos24.spring_cgv.domain.movie.dto.response.CinemaResponse;
import com.ceos24.spring_cgv.domain.movie.entity.Cinema;
import com.ceos24.spring_cgv.domain.movie.exception.CinemaException;
import com.ceos24.spring_cgv.domain.movie.exception.code.CinemaErrorCode;
import com.ceos24.spring_cgv.domain.movie.repository.CinemaRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CinemaService {

    private final CinemaRepository cinemaRepository;

    /***
     * 함수 기능: 새로운 영화관을 등록합니다.
     * @param request 새로운 영화관 지역, 주소
     * @return CinemaResponse 등록된 영화관 ID, 지역, 주소
     */
    @Transactional
    public CinemaResponse createCinema(CinemaCreateRequest request) {

        // 같은 지역과 주소가 존재하는지 검사한다.
        if (cinemaRepository.existsByRegionAndAddress(request.region(), request.address())) {
            throw new CinemaException(CinemaErrorCode.CINEMA_ALREADY_EXISTS);
        }

        Cinema cinema = Cinema.builder()
                .name(request.name())
                .region(request.region())
                .address(request.address())
                .build();

        return CinemaResponse.from(cinemaRepository.save(cinema));
    }

    /***
     * 함수 기능: 전체 영화관 목록을 조회합니다.
     * @return List<CinemaResponse> 모든 영화관 ID, 지역, 주소
     */
    public List<CinemaResponse> findAllCinema() {

        return cinemaRepository.findAll().stream()
                .map(CinemaResponse::from)
                .toList();
    }

    /***
     * 함수 기능: 해당 영화관을 조회합니다.
     * @param cinemaId 영화관 ID
     * @return CinemaResponse 해당 영화관 ID, 지역, 주소
     */
    public CinemaResponse findCinema(Long cinemaId) {

        Cinema cinema = findCinemaById(cinemaId);

        return CinemaResponse.from(cinema);
    }

    /***
     * 함수 기능: 해당 영화관 정보를 업데이트한다.
     * @param cinemaId 영화관 ID
     * @param request 변경할 영화관 정보 (지역, 주소)
     * @return CinemaResponse 변경된 영화관의 ID, 지역, 주소
     */
    @Transactional
    public CinemaResponse updateCinema(Long cinemaId, CinemaUpdateRequest request) {

        Cinema cinema = findCinemaById(cinemaId);

        // 자신 제외 같은 지역과 주소가 존재하는지 검사한다.
        if (cinemaRepository.existsByRegionAndAddressAndIdNot(request.region(), request.address(), cinemaId)) {
            throw new CinemaException(CinemaErrorCode.CINEMA_ALREADY_EXISTS);
        }

        cinema.update(request.name(), request.region(), request.address());

        return CinemaResponse.from(cinema);
    }

    /***
     * 함수 기능: 해당 영화관을 삭제한다.
     * @param cinemaId 영화관 ID
     */
    @Transactional
    public void deleteCinema(Long cinemaId) {

        Cinema cinema = findCinemaById(cinemaId);

        cinemaRepository.delete(cinema);
    }

    private @NonNull Cinema findCinemaById(Long cinemaId) {
        return cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new CinemaException(CinemaErrorCode.CINEMA_NOT_FOUND));
    }
}