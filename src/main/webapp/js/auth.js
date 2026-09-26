const USERS_KEY = "foodAdvisor.demo.users.v1";
const SESSION_KEY = "foodAdvisor.demo.session.v1";
const SESSION_LENGTH_MS = 30 * 60 * 1000;
const HASH_ITERATIONS = 120000;

function readUsers() {
    try {
        const value = JSON.parse(localStorage.getItem(USERS_KEY) || "[]");
        return Array.isArray(value) ? value : [];
    } catch {
        return [];
    }
}

function writeUsers(users) {
    localStorage.setItem(USERS_KEY, JSON.stringify(users));
}

function toBase64(bytes) {
    return btoa(String.fromCharCode(...bytes));
}

async function hashValue(value, saltBase64) {
    if (!window.crypto?.subtle) {
        throw new Error("암호화 기능을 사용할 수 없습니다. localhost에서 열어주세요.");
    }

    const salt = Uint8Array.from(atob(saltBase64), c => c.charCodeAt(0));
    const key = await crypto.subtle.importKey(
        "raw",
        new TextEncoder().encode(value),
        "PBKDF2",
        false,
        ["deriveBits"]
    );

    const bits = await crypto.subtle.deriveBits(
        { name: "PBKDF2", salt, iterations: HASH_ITERATIONS, hash: "SHA-256" },
        key,
        256
    );

    return toBase64(new Uint8Array(bits));
}

async function makeSecret(value) {
    const salt = new Uint8Array(16);
    crypto.getRandomValues(salt);
    const saltBase64 = toBase64(salt);

    return {
        salt: saltBase64,
        hash: await hashValue(value, saltBase64)
    };
}

async function matchesSecret(value, salt, expectedHash) {
    return await hashValue(value, salt) === expectedHash;
}

function normalizeAnswer(value) {
    return value.trim().toLocaleLowerCase("ko-KR");
}

function calculateCalories(user) {
    const base = 10 * user.weightKg + 6.25 * user.heightCm - 5 * user.age;
    const bmr = user.gender === "남" ? base + 5 : base - 161;

    const activity = {
        SEDENTARY: 1.2,
        LIGHTLY_ACTIVE: 1.375,
        MODERATELY_ACTIVE: 1.55,
        VERY_ACTIVE: 1.725,
        EXTRA_ACTIVE: 1.9
    }[user.actLevel] || 1.2;

    const adjustment = {
        체중감량: -500,
        체중유지: 0,
        근육량증가: 300
    }[user.goal] || 0;

    return Math.max(1200, Math.round(bmr * activity) + adjustment);
}

function maskEmail(email) {
    const [name, domain] = email.split("@");
    if (!domain) return "***";

    const visibleCount = Math.ceil(name.length / 2);
    const maskedCount = Math.max(1, name.length - visibleCount);
    return name.slice(0, visibleCount) + "*".repeat(maskedCount) + "@" + domain;
}

function setMessage(id, text, isError = false) {
    const element = document.getElementById(id);
    if (!element) return;
    element.textContent = text;
    element.style.color = isError ? "#c0392b" : "#16856f";
}

async function handleSignup(event) {
    event.preventDefault();

    try {
        const email = document.getElementById("signupEmail").value.trim().toLowerCase();
        const password = document.getElementById("signupPassword").value;
        const confirm = document.getElementById("signupPasswordConfirm").value;
        const answer = normalizeAnswer(document.getElementById("securityAnswer").value);

        if (password.length < 8) {
            setMessage("signupMessage", "비밀번호는 8자 이상이어야 합니다.", true);
            return;
        }
        if (password !== confirm) {
            setMessage("signupMessage", "비밀번호가 서로 다릅니다.", true);
            return;
        }

        const users = readUsers();
        if (users.some(user => user.email === email)) {
            setMessage("signupMessage", "이미 가입된 이메일입니다.", true);
            return;
        }

        const user = {
            email,
            name: document.getElementById("signupName").value.trim(),
            gender: document.getElementById("signupGender").value,
            age: Number(document.getElementById("signupAge").value),
            heightCm: Number(document.getElementById("signupHeight").value),
            weightKg: Number(document.getElementById("signupWeight").value),
            actLevel: document.getElementById("signupActivity").value,
            goal: document.getElementById("signupGoal").value,
            preferance: document.getElementById("signupPreference").value,
            securityQuestion: document.getElementById("securityQuestion").value
        };

        if (!user.name || !answer || !user.securityQuestion
            || !Number.isFinite(user.age) || user.age <= 0
            || !Number.isFinite(user.heightCm) || user.heightCm <= 0
            || !Number.isFinite(user.weightKg) || user.weightKg <= 0) {
            setMessage("signupMessage", "입력값을 확인해 주세요.", true);
            return;
        }

        const passwordSecret = await makeSecret(password);
        const answerSecret = await makeSecret(answer);

        user.passwordSalt = passwordSecret.salt;
        user.passwordHash = passwordSecret.hash;
        user.answerSalt = answerSecret.salt;
        user.answerHash = answerSecret.hash;
        user.targetDailyCalories = calculateCalories(user);

        users.push(user);
        writeUsers(users);

        window.location.href = "login.html";
    } catch (error) {
        setMessage("signupMessage", error.message || "가입 중 오류가 발생했습니다.", true);
    }
}

async function handleLogin(event) {
    event.preventDefault();

    try {
        const email = document.getElementById("loginEmail").value.trim().toLowerCase();
        const password = document.getElementById("loginPassword").value;
        const user = readUsers().find(item => item.email === email);

        if (!user || !await matchesSecret(password, user.passwordSalt, user.passwordHash)) {
            setMessage("loginMessage", "이메일 또는 비밀번호를 확인해 주세요.", true);
            return;
        }

        localStorage.setItem(SESSION_KEY, JSON.stringify({
            email,
            expiresAt: Date.now() + SESSION_LENGTH_MS
        }));

        window.location.href = "index.html";
    } catch (error) {
        setMessage("loginMessage", error.message || "로그인 중 오류가 발생했습니다.", true);
    }
}

async function handleFindId(event) {
    event.preventDefault();

    const name = document.getElementById("findName").value.trim().toLowerCase();
    const question = document.getElementById("findQuestion").value;
    const answer = normalizeAnswer(document.getElementById("findAnswer").value);
    const matches = [];

    try {
        for (const user of readUsers()) {
            if (user.name.toLowerCase() === name && user.securityQuestion === question) {
                if (await matchesSecret(answer, user.answerSalt, user.answerHash)) {
                    matches.push(maskEmail(user.email));
                }
            }
        }

        setMessage(
            "findIdMessage",
            matches.length ? `아이디: ${matches.join(", ")}` : "일치하는 계정을 찾지 못했습니다."
        );
    } catch (error) {
        setMessage("findIdMessage", error.message || "확인 중 오류가 발생했습니다.", true);
    }
}

async function handleResetPassword(event) {
    event.preventDefault();

    const email = document.getElementById("resetEmail").value.trim().toLowerCase();
    const question = document.getElementById("resetQuestion").value;
    const answer = normalizeAnswer(document.getElementById("resetAnswer").value);
    const password = document.getElementById("newPassword").value;
    const confirm = document.getElementById("newPasswordConfirm").value;

    if (password.length < 8) {
        setMessage("resetPasswordMessage", "새 비밀번호는 8자 이상이어야 합니다.", true);
        return;
    }
    if (password !== confirm) {
        setMessage("resetPasswordMessage", "새 비밀번호가 서로 다릅니다.", true);
        return;
    }

    try {
        const users = readUsers();
        const user = users.find(item => item.email === email);

        if (!user || user.securityQuestion !== question
            || !await matchesSecret(answer, user.answerSalt, user.answerHash)) {
            setMessage("resetPasswordMessage", "아이디 또는 본인 확인 정보를 확인해 주세요.", true);
            return;
        }

        const secret = await makeSecret(password);
        user.passwordSalt = secret.salt;
        user.passwordHash = secret.hash;
        writeUsers(users);

        setMessage("resetPasswordMessage", "비밀번호를 변경했습니다. 로그인해 주세요.");
    } catch (error) {
        setMessage("resetPasswordMessage", error.message || "변경 중 오류가 발생했습니다.", true);
    }
}

function renderMainPage() {
    const loggedOut = document.getElementById("loggedOutView");
    const loggedIn = document.getElementById("loggedInView");
    if (!loggedOut || !loggedIn) return;

    let session;
    try {
        session = JSON.parse(localStorage.getItem(SESSION_KEY) || "null");
    } catch {
        session = null;
    }

    const user = session && Date.now() < session.expiresAt
        ? readUsers().find(item => item.email === session.email)
        : null;

    if (!user) {
        localStorage.removeItem(SESSION_KEY);
        loggedOut.hidden = false;
        loggedIn.hidden = true;
        return;
    }

    loggedOut.hidden = true;
    loggedIn.hidden = false;
    document.getElementById("welcomeText").textContent = `${user.name} 님, 환영합니다`;
    document.getElementById("profileDemographics").textContent =
        `${user.gender}성 / ${user.age}세`;
    document.getElementById("profileBody").textContent =
        `${user.heightCm}cm / ${user.weightKg}kg`;
    document.getElementById("profileGoal").textContent = user.goal;
    document.getElementById("profilePreference").textContent = user.preferance;
    document.getElementById("profileCalories").textContent =
        `${user.targetDailyCalories} kcal`;
}

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("signupForm")?.addEventListener("submit", handleSignup);
    document.getElementById("loginForm")?.addEventListener("submit", handleLogin);
    document.getElementById("findIdForm")?.addEventListener("submit", handleFindId);
    document.getElementById("resetPasswordForm")?.addEventListener("submit", handleResetPassword);

    document.getElementById("logoutButton")?.addEventListener("click", () => {
        localStorage.removeItem(SESSION_KEY);
        renderMainPage();
    });

    renderMainPage();
});