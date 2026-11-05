package edu.secure.assign.web;


import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 공통 예외 처리 */
// 사용자에게는 규격화 된 메시지만 응답, 상세 원인(스택 트레이스)은 서버 로그에만 기록
@RestControllerAdvice
public class GlobalExceptionHandler {
	
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	
	/** 401(미로그인), 404(없는 자원/타인 자원) 등 코드에서 의도한 상태 코드는 그대로 유지 */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
		return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", "요청을 처리할 수 없습니다."));
	}
	
	
	/** 입력값 오류 (음수 금액, 범위 밖 금리, 숫자가 아닌 금리, 허용되지 않은 정렬 기준 -> 400 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "잘못 된 요청입니다."));
	}
	
	/** @Valid 검증 실패 -> 400. 어느 규칙에 걸렸는지 상세는 알려주지 않음 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
	    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "입력값을 확인해 주세요."));
	}	
	
	/** 그 외 예상하지 못한 오류 -> 500. 추적용 errorId 만 내려주고 상세는 로그에만 */
	@ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
		String errorId = UUID.randomUUID().toString();
		log.error("처리 중 오류 errorId={}", errorId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        		.body(Map.of("message", "요청을 처리할 수 없습니다.", "errorId", errorId));
    }
}
