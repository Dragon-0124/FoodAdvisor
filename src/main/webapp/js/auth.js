/*
 * 로그인/회원가입/아이디 찾기/비밀번호 재설정 - 서버 AuthServlet(api/auth/*) 호출 (js/api.js 필요)
 * 계정 정보는 서버 DB(users)에 저장되고, 로그인 상태는 서버 세션으로 유지됩니다.
 */
(() => {
  const api = window.FoodAdvisorApi;
  const val = id => document.getElementById(id)?.value?.trim() || '';
  const optionalNumber = id => val(id) === '' ? null : Number(val(id));  // 빈칸이면 null (선택 입력)
  const message = (id, text, error = false) => {
    const el = document.getElementById(id);
    if (el) { el.textContent = text; el.style.color = error ? '#b42318' : '#17805d'; }
  };

  // 요청 중에는 제출 버튼을 잠가 중복 제출(중복 가입 등)을 막습니다.
  async function submitting(form, task) {
    const buttons = form.querySelectorAll('button');
    buttons.forEach(button => { button.disabled = true; });
    try { await task(); } finally { buttons.forEach(button => { button.disabled = false; }); }
  }

  document.getElementById('signupForm')?.addEventListener('submit', e => {
    e.preventDefault();
    if (val('signupPassword') !== val('signupPasswordConfirm')) return message('signupMessage', '비밀번호가 서로 다릅니다.', true);
    // UserDTO 필드명에 맞춰 전송합니다. (나이/키/체중은 숫자여야 합니다.)
    const body = {
      email: val('signupEmail').toLowerCase(),
      password: document.getElementById('signupPassword').value,
      name: val('signupName'),
      gender: val('signupGender'),
      age: Number(val('signupAge')),
      heightCm: Number(val('signupHeight')),
      weightKg: Number(val('signupWeight')),
      muscleKg: optionalNumber('signupMuscle'),      // 선택: 신체 변화 기록의 첫 기록에 함께 저장
      bodyFatPct: optionalNumber('signupBodyFat'),   // 선택
      actLevel: val('signupActivity'),
      goal: val('signupGoal'),
      preference: val('signupPreference'),
      securityQuestion: val('securityQuestion'),
      securityAnswer: val('securityAnswer')
    };
    submitting(e.target, async () => {
      try {
        await api.post('auth/signup', body);
        message('signupMessage', '계정이 생성되었습니다. 로그인 화면으로 이동합니다.');
        setTimeout(() => location.href = 'login.html', 650);
      } catch (error) {
        message('signupMessage', error.message, true);
      }
    });
  });

  document.getElementById('loginForm')?.addEventListener('submit', e => {
    e.preventDefault();
    const body = { email: val('loginEmail').toLowerCase(), password: document.getElementById('loginPassword').value };
    submitting(e.target, async () => {
      try {
        await api.post('auth/login', body);
        location.href = 'dashboard.html';
      } catch (error) {
        message('loginMessage', error.message, true);
      }
    });
  });

  document.getElementById('findIdForm')?.addEventListener('submit', e => {
    e.preventDefault();
    const body = { name: val('findName'), securityQuestion: val('findQuestion'), securityAnswer: val('findAnswer') };
    submitting(e.target, async () => {
      try {
        const data = await api.post('auth/find-id', body);
        message('findIdMessage', `가입 이메일: ${data.email}`);   // 서버가 일부를 가려서(마스킹) 내려줍니다.
      } catch (error) {
        message('findIdMessage', error.message, true);
      }
    });
  });

  // 비밀번호 재설정: 1) 이메일로 인증 코드 받기  2) 인증 코드 확인  3) 새 비밀번호로 변경
  // 인증 완료 전에는 새 비밀번호 입력칸과 저장 버튼을 잠가 둡니다.
  const setResetVerified = verified => {
    ['newPassword', 'newPasswordConfirm', 'resetSubmitButton'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.disabled = !verified;
    });
    const code = document.getElementById('resetCode');
    const verifyButton = document.getElementById('verifyCodeButton');
    if (code) code.readOnly = verified;
    if (verifyButton) {
      verifyButton.disabled = verified;
      verifyButton.textContent = verified ? '인증 완료' : '확인';
    }
  };

  // 인증 후 이메일을 바꾸면 다시 인증해야 합니다.
  document.getElementById('resetEmail')?.addEventListener('input', () => setResetVerified(false));

  document.getElementById('verifyCodeButton')?.addEventListener('click', async e => {
    const button = e.currentTarget;
    const email = val('resetEmail').toLowerCase();
    const code = val('resetCode');
    if (!email) return message('resetPasswordMessage', '이메일을 입력해 주세요.', true);
    if (!/^\d{6}$/.test(code)) return message('resetPasswordMessage', '6자리 인증 코드를 입력해 주세요.', true);
    button.disabled = true;
    try {
      const data = await api.post('auth/password/verify', { email, code });
      setResetVerified(true);
      message('resetPasswordMessage', data.message);
      document.getElementById('newPassword')?.focus();
    } catch (error) {
      button.disabled = false;
      message('resetPasswordMessage', error.message, true);
    }
  });

  document.getElementById('sendCodeButton')?.addEventListener('click', async e => {
    const button = e.currentTarget;
    const email = val('resetEmail').toLowerCase();
    if (!email) return message('resetPasswordMessage', '이메일을 입력해 주세요.', true);
    button.disabled = true;
    setResetVerified(false);   // 코드를 새로 받으면 이전 인증은 무효
    try {
      const data = await api.post('auth/password/code', { email });
      message('resetPasswordMessage', data.message + ' (3분 이내에 입력해 주세요.)');
    } catch (error) {
      message('resetPasswordMessage', error.message, true);
    } finally {
      button.disabled = false;
    }
  });

  document.getElementById('resetPasswordForm')?.addEventListener('submit', e => {
    e.preventDefault();
    if (val('newPassword') !== val('newPasswordConfirm')) return message('resetPasswordMessage', '새 비밀번호가 서로 다릅니다.', true);
    const body = { email: val('resetEmail').toLowerCase(), newPassword: document.getElementById('newPassword').value };
    submitting(e.target, async () => {
      try {
        await api.post('auth/password/reset', body);
        message('resetPasswordMessage', '비밀번호가 변경되었습니다. 로그인 화면으로 이동합니다.');
        setTimeout(() => location.href = 'login.html', 900);
      } catch (error) {
        message('resetPasswordMessage', error.message, true);
      }
    });
  });
})();
