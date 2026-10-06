package com.ceos24.spring_cgv.domain.movie.service;

import com.ceos24.spring_cgv.domain.movie.dto.request.CinemaCreateRequest;
import com.ceos24.spring_cgv.domain.movie.dto.request.CinemaUpdateRequest;
import com.ceos24.spring_cgv.domain.movie.dto.response.CinemaResponse;
import com.ceos24.spring_cgv.domain.movie.entity.Cinema;
import com.ceos24.spring_cgv.domain.movie.exception.CinemaException;
import com.ceos24.spring_cgv.domain.movie.exception.code.CinemaErrorCode;
import com.ceos24.spring_cgv.domain.movie.repository.CinemaRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CinemaService 단위 테스트")
class CinemaServiceTest {

    @Mock
    private CinemaRepository cinemaRepository;

    @InjectMocks
    private CinemaService cinemaService;

    private static final String NAME = "CGV 강남";
    private static final String REGION = "강남";
    private static final String ADDRESS = "주소";

    private Cinema createCinema(){
        Cinema cinema = Cinema.builder()
                .name(CinemaServiceTest.NAME)
                .region(CinemaServiceTest.REGION)
                .address(CinemaServiceTest.ADDRESS)
                .build();

        ReflectionTestUtils.setField(cinema, "id", 1L);
        return cinema;
    }

    @Nested
    @DisplayName("영화관 등록")
    class 영화관_등록 {

        @Test
        @DisplayName("영화관이 정상으로 등록되는지 확인한다.")
        void 영화관_등록_성공(){

            // given
            CinemaCreateRequest request = new CinemaCreateRequest(NAME, REGION, ADDRESS);
            given(cinemaRepository.existsByRegionAndAddress(REGION, ADDRESS)).willReturn(false);
            given(cinemaRepository.save(any(Cinema.class))).willReturn(createCinema());

            // when
            CinemaResponse response = cinemaService.createCinema(request);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo(NAME);
            assertThat(response.region()).isEqualTo(REGION);
            assertThat(response.address()).isEqualTo(ADDRESS);
        }

        @Test
        @DisplayName("같은 지역과 주소의 영화관이 존재하면 실패한다.")
        void 영화관_중복_등록_실패(){

            // given
            CinemaCreateRequest request = new CinemaCreateRequest(NAME, REGION, ADDRESS);
            given(cinemaRepository.existsByRegionAndAddress(REGION,ADDRESS)).willReturn(true);

            // when, then
            Assertions.assertThatThrownBy(() ->
                    cinemaService.createCinema(request))
                    .isInstanceOf(CinemaException.class)
                    .extracting(e -> ((CinemaException) e).getErrorCode())
                    .isEqualTo(CinemaErrorCode.CINEMA_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("영화관 전체 조회")
    class 영화관_전체_조회{

        @Test
        @DisplayName("저장된 모든 영화관 목록을 반환한다.")
        void 영화관_전체_조회_성공(){

            // given
            given(cinemaRepository.findAll()).willReturn(List.of(createCinema()));

            // when
            List<CinemaResponse> response = cinemaService.findAllCinema();

            // then
            assertThat(response).hasSize(1);
            assertThat(response.getFirst().id()).isEqualTo(1L);
            assertThat(response.getFirst().region()).isEqualTo(REGION);
        }
    }

    @Nested
    @DisplayName("영화관 단건 조회")
    class 영화관_단건_조회 {

        @Test
        @DisplayName("존재하는 ID로 조회하면 해당 영화관을 반환한다.")
        void 영화관_단건_조회_성공(){

            // given
            given(cinemaRepository.findById(1L)).willReturn(Optional.of(createCinema()));

            // when
            CinemaResponse response = cinemaService.findCinema(1L);

            // then
            Assertions.assertThat(response.id()).isEqualTo(1L);
            Assertions.assertThat(response.region()).isEqualTo(REGION);
            Assertions.assertThat(response.address()).isEqualTo(ADDRESS);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 실패한다.")
        void 영화관_단건_조회_실패(){

            // given
            given(cinemaRepository.findById(2L)).willReturn(Optional.empty());

            // when & then
            Assertions.assertThatThrownBy(() ->
                            cinemaService.findCinema(2L))
                    .isInstanceOf(CinemaException.class)
                    .extracting(e -> ((CinemaException) e).getErrorCode())
                    .isEqualTo(CinemaErrorCode.CINEMA_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("영화관 수정")
    class 영화관_수정 {

        @Test
        @DisplayName("영화관 정보가 정상으로 수정되는지 확인한다.")
        void 영화관_수정_성공(){

            // given
            Cinema cinema = createCinema();
            CinemaUpdateRequest request = new CinemaUpdateRequest("CGV 의왕", "의왕", "포일로30");
            given(cinemaRepository.findById(1L)).willReturn(Optional.of(cinema));
            given(cinemaRepository.existsByRegionAndAddressAndIdNot("의왕", "포일로30", 1L))
                    .willReturn(false);

            // when
            CinemaResponse response = cinemaService.updateCinema(1L, request);

            // then
            assertThat(response.name()).isEqualTo("CGV 의왕");
            assertThat(response.region()).isEqualTo("의왕");
            assertThat(response.address()).isEqualTo("포일로30");
        }

        @Test
        @DisplayName("존재하지 않는 ID로 수정하면 실패한다.")
        void 영화관_수정_실패(){

            // given
            CinemaUpdateRequest request = new CinemaUpdateRequest("CGV 의왕", "의왕", "포일로30");
            given(cinemaRepository.findById(2L)).willReturn(Optional.empty());

            // when, then
            Assertions.assertThatThrownBy(() -> {
               cinemaService.updateCinema(2L, request);
            }).isInstanceOf(CinemaException.class)
                    .extracting(e -> ((CinemaException) e).getErrorCode())
                    .isEqualTo(CinemaErrorCode.CINEMA_NOT_FOUND);
        }
    }
}