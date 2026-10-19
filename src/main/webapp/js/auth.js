/* DEMO ONLY: browser-local account simulation. Replace with /api/auth/* calls after backend contract is confirmed. */
(() => {
  const USERS_KEY = 'foodAdvisor.demo.users.v1';
  const SESSION_KEY = 'foodAdvisor.demo.session.v1';
  const read = key => { try { return JSON.parse(localStorage.getItem(key) || '[]'); } catch { return []; } };
  const write = (key, value) => localStorage.setItem(key, JSON.stringify(value));
  const val = id => document.getElementById(id)?.value?.trim() || '';
  const message = (id, text, error = false) => { const el = document.getElementById(id); if (el) { el.textContent = text; el.style.color = error ? '#b42318' : '#17805d'; } };
  const users = () => read(USERS_KEY);

  document.getElementById('signupForm')?.addEventListener('submit', e => {
    e.preventDefault();
    const email = val('signupEmail').toLowerCase(), password = val('signupPassword');
    if (password !== val('signupPasswordConfirm')) return message('signupMessage', '비밀번호가 서로 다릅니다.', true);
    const list = users();
    if (list.some(u => u.email === email)) return message('signupMessage', '이미 가입된 이메일입니다.', true);
    list.push({ email, password, name: val('signupName'), gender: val('signupGender'), age: val('signupAge'), height: val('signupHeight'), weight: val('signupWeight'), activity: val('signupActivity'), goal: val('signupGoal'), preference: val('signupPreference'), question: val('securityQuestion'), answer: val('securityAnswer') });
    write(USERS_KEY, list);
    message('signupMessage', '계정이 생성되었습니다. 로그인 화면으로 이동합니다.');
    setTimeout(() => location.href = 'login.html', 650);
  });

  document.getElementById('loginForm')?.addEventListener('submit', e => {
    e.preventDefault();
    const email = val('loginEmail').toLowerCase(), password = val('loginPassword');
    const user = users().find(u => u.email === email && u.password === password);
    if (!user) return message('loginMessage', '아이디 또는 비밀번호를 확인해 주세요.', true);
    write(SESSION_KEY, { email: user.email, name: user.name });
    location.href = 'dashboard.html';
  });

  document.getElementById('findIdForm')?.addEventListener('submit', e => {
    e.preventDefault();
    const user = users().find(u => u.name === val('findName') && u.question === val('findQuestion') && u.answer === val('findAnswer'));
    message('findIdMessage', user ? `가입 이메일: ${user.email}` : '일치하는 계정을 찾지 못했습니다.', !user);
  });

  document.getElementById('resetPasswordForm')?.addEventListener('submit', e => {
    e.preventDefault();
    if (val('newPassword') !== val('newPasswordConfirm')) return message('resetPasswordMessage', '새 비밀번호가 서로 다릅니다.', true);
    const list = users(), user = list.find(u => u.email === val('resetEmail').toLowerCase() && u.question === val('resetQuestion') && u.answer === val('resetAnswer'));
    if (!user) return message('resetPasswordMessage', '계정 정보가 일치하지 않습니다.', true);
    user.password = val('newPassword'); write(USERS_KEY, list);
    message('resetPasswordMessage', '비밀번호가 변경되었습니다. 로그인해 주세요.');
  });

  // Dashboard can use this to display a demo name and sign out.
  window.FoodAdvisorDemoAuth = {
    getSession: () => { try { return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null'); } catch { return null; } },
    logout: () => { localStorage.removeItem(SESSION_KEY); location.href = 'login.html'; }
  };
})();
