// 공통 API 호출 유틸. 로그인 API(#5)가 완성되면 이 파일에 로그인/토큰 저장 로직을 채웁니다.

function apiFetch(path, options = {}) {
  const token = localStorage.getItem('accessToken');
  const headers = Object.assign({}, options.headers, token ? { Authorization: `Bearer ${token}` } : {});
  return fetch(path, Object.assign({}, options, { headers }));
}
