package mrs.domain.service.reservation;

import java.util.List;

import mrs.domain.model.*;
import mrs.domain.repository.reservation.ReservationRepository;
import mrs.domain.repository.room.ReservableRoomRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReservationService {
	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	ReservableRoomRepository reservableRoomRepository;

	public List<Reservation> findReservations(ReservableRoomId reservableRoomId) {
		return reservationRepository
				.findByReservableRoom_ReservableRoomIdOrderByStartTimeAsc(
						reservableRoomId);
	}

	public Reservation findOne(Integer reservationId) {
		return reservationRepository.findOne(reservationId);
	}

	public Reservation reserve(Reservation reservation) {
		ReservableRoomId reservableRoomId = reservation.getReservableRoom()
				.getReservableRoomId();
		// 悲観ロック
		ReservableRoom reservable = reservableRoomRepository
				.findOneForUpdateByReservableRoomId(reservableRoomId);
		if (reservable == null) {
			throw new UnavailableReservationException("入力の日付・部屋の組み合わせは予約できません。");
		}
		// 該当の日付・部屋の全予約情報をReservableRoomテーブルから取得し、重複をチェック
		boolean overlap = reservationRepository
				.findByReservableRoom_ReservableRoomIdOrderByStartTimeAsc(
						reservableRoomId)
				.stream().anyMatch(x -> x.overlap(reservation));
		if (overlap) {
			throw new AlreadyReservedException("入力の時間帯は既に予約済みです。");
		}
		// 予約情報の登録
		reservationRepository.save(reservation);
		return reservation;
	}


	// USERロールの場合は予約者がログインユーザーの場合に取り消し可能
	// ADMINロールの場合は全予約取り消し可能
	@PreAuthorize("hasRole('ADMIN') or #reservation.user.userId == principal.user.userId")
	public void cancel(@P("reservation") Reservation reservation) {
		reservationRepository.delete(reservation);
	}
}
