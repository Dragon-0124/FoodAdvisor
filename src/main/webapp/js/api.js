/*
 * FoodAdvisor API helper - 서버(Servlet)와 통신하는 공통 모듈입니다.
 * - 경로는 컨텍스트 경로(/FoodAdvisor 등)와 무관하게 동작하도록 상대경로(api/...)를 사용합니다.
 *   따라서 HTML 파일을 더블 클릭(file://)해서 열면 동작하지 않고, Tomcat 에서 실행한 주소로 열어야 합니다.
 * - 로그인 상태는 서버 세션(쿠키)으로 유지됩니다. (브라우저 localStorage 를 사용하지 않습니다.)
 */
(() => {
  class ApiError extends Error {
    constructor(message, status) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
    }
  }

  async function request(path, { method = 'GET', body } = {}) {
    const options = {
      method,
      headers: { Accept: 'application/json' },
      credentials: 'same-origin'
    };
    if (body !== undefined) {
      options.headers['Content-Type'] = 'application/json; charset=UTF-8';
      options.body = JSON.stringify(body);
    }

    let response;
    try {
      response = await fetch('api/' + path, options);
    } catch (error) {
      throw new ApiError('서버에 연결할 수 없습니다. 서버(Tomcat)가 실행 중인지, 주소(http://...)로 접속했는지 확인해 주세요.', 0);
    }

    const data = await response.json().catch(() => null);
    if (!response.ok) {
      throw new ApiError((data && data.error) || '요청 처리 중 오류가 발생했습니다. (' + response.status + ')', response.status);
    }
    return data;
  }

  const get = path => request(path);
  const post = (path, body) => request(path, { method: 'POST', body: body === undefined ? {} : body });

  // 현재 로그인 세션 조회: 로그인 상태면 { email, user, ... }, 아니면 null
  async function getSession() {
    try {
      const data = await get('auth/session');
      return data && data.loggedIn ? data : null;
    } catch (error) {
      return null;
    }
  }

  // 로그인이 필요한 페이지에서 호출: 로그인 상태가 아니면 로그인 화면으로 보냅니다.
  async function requireLogin() {
    const session = await getSession();
    if (!session) {
      location.replace('login.html');
      return null;
    }
    return session;
  }

  async function logout() {
    try { await post('auth/logout'); } catch (error) { /* 세션이 이미 없어도 로그인 화면으로 이동 */ }
    location.href = 'login.html';
  }

  // 화면에서 공통으로 쓰는 오류 처리: 세션 만료(401)는 로그인 화면으로, 그 외는 메시지 표시
  let redirecting = false;
  function handleError(error, fallbackMessage) {
    if (error && error.status === 401) {
      if (!redirecting) {  // 연속된 요청이 동시에 401 을 받아도 안내는 한 번만
        redirecting = true;
        alert('로그인이 필요하거나 세션이 만료되었습니다. 다시 로그인해 주세요.');
        location.replace('login.html');
      }
      return;
    }
    console.error(error);
    alert((error && error.message) || fallbackMessage || '오류가 발생했습니다.');
  }

  window.FoodAdvisorApi = { request, get, post, ApiError, getSession, requireLogin, logout, handleError };
})();
