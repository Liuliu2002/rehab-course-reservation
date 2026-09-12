const appState = {
  role: localStorage.getItem('rehab-role') || 'student',
  user: JSON.parse(localStorage.getItem('rehab-user') || 'null'),
  selectedCourse: null,
  selectedTeacher: null,
  editingScheduleId: null,
  ws: null
};

const navMap = {
  student: [
    { id: 'loginView', title: '学生登录', label: '登录' },
    { id: 'studentCoursesView', title: '课程预约', label: '课程预约' },
    { id: 'studentAppointmentsView', title: '我的预约', label: '我的预约' }
  ],
  teacher: [
    { id: 'loginView', title: '教师登录', label: '登录' },
    { id: 'teacherDashboardView', title: '预约审核', label: '预约审核' },
    { id: 'teacherCoursesView', title: '课程管理', label: '课程管理', adminOnly: true },
    { id: 'teacherManagementView', title: '教师管理', label: '教师管理', adminOnly: true },
    { id: 'teacherScheduleView', title: '排班管理', label: '排班管理' }
  ]
};

const statusText = {
  1: '待确认',
  2: '已确认',
  3: '已完成',
  4: '已取消',
  5: '已拒绝'
};

const loginContent = {
  student: {
    heading: '登录后开始预约康复课程',
    description: '浏览康复课程与教师排班，选择合适时段提交预约。',
    phonePlaceholder: '输入手机号，新用户将自动注册'
  },
  teacher: {
    heading: '登录后管理课程预约',
    description: '维护个人排班并审核学生预约；管理员还可以维护课程信息。',
    phonePlaceholder: '请输入教师手机号'
  }
};

const $ = selector => document.querySelector(selector);

function toast(message) {
  const toastEl = $('#toast');
  toastEl.textContent = message;
  toastEl.classList.add('show');
  setTimeout(() => toastEl.classList.remove('show'), 2600);
}

function requireLogin() {
  if (!localStorage.getItem('rehab-token')) {
    toast('请先登录');
    showView('loginView');
    return false;
  }
  return true;
}

async function setRole(role) {
  const previousRole = appState.role;
  appState.role = role;
  if (previousRole !== role) {
    try {
      if (localStorage.getItem('rehab-token')) await RehabApi.logout();
    } catch (error) {
      // 本地状态仍需清理；服务端会话最迟在 Refresh Token 到期时自动删除。
    }
    localStorage.removeItem('rehab-token');
    localStorage.removeItem('rehab-user');
    appState.user = null;
    if (appState.ws) {
      appState.ws.onclose = null;
      appState.ws.close();
      appState.ws = null;
    }
    $('#passwordInput').value = '';
  }
  localStorage.setItem('rehab-role', role);
  document.querySelectorAll('.role-btn').forEach(btn => btn.classList.toggle('active', btn.dataset.role === role));
  updateLoginContent();
  renderNav();
  updateAccount();
  updateConnectionStatus();
  showView('loginView');
}

function renderNav() {
  const navList = $('#navList');
  const authenticated = Boolean(localStorage.getItem('rehab-token') && appState.user);
  const visibleItems = navMap[appState.role].filter(item => {
    if (!authenticated) return item.id === 'loginView';
    return !item.adminOnly || appState.user.role === 'admin';
  });
  navList.innerHTML = visibleItems.map(item => `
    <button class="nav-item" data-view="${item.id}">${item.label}</button>
  `).join('');
  navList.querySelectorAll('.nav-item').forEach(button => {
    button.addEventListener('click', () => showView(button.dataset.view));
  });
}

function updateLoginContent() {
  const content = loginContent[appState.role];
  $('#loginHeading').textContent = content.heading;
  $('#loginDescription').textContent = content.description;
  $('#phoneInput').placeholder = content.phonePlaceholder;
}

function showView(viewId) {
  if (viewId !== 'loginView' && !requireLogin()) {
    return;
  }
  document.querySelectorAll('.view').forEach(view => view.classList.toggle('active', view.id === viewId));
  document.querySelectorAll('.nav-item').forEach(item => item.classList.toggle('active', item.dataset.view === viewId));
  const current = navMap[appState.role].find(item => item.id === viewId);
  $('#pageTitle').textContent = current ? current.title : '康复课程预约系统';

  if (viewId === 'studentCoursesView') loadStudentData();
  if (viewId === 'studentAppointmentsView') loadStudentAppointments();
  if (viewId === 'teacherDashboardView') loadTeacherAppointments();
  if (viewId === 'teacherCoursesView') loadAdminCourses();
  if (viewId === 'teacherManagementView') loadAdminTeachers();
  if (viewId === 'teacherScheduleView') loadTeacherSchedules();
}

function updateAccount() {
  const roleName = appState.role === 'student' ? '学生端' : '教师端';
  $('#accountName').textContent = appState.user ? appState.user.name : '未登录';
  const authRole = appState.user && appState.user.role === 'admin' ? '管理员' : roleName;
  $('#accountRole').textContent = appState.user ? `${authRole} · ID ${appState.user.id}` : `${roleName}未登录`;
  $('#logoutBtn').hidden = !appState.user;
}

function updateConnectionStatus() {
  if (!localStorage.getItem('rehab-token') || !appState.user) {
    $('#socketStatus').textContent = appState.role === 'teacher'
      ? '登录后连接消息通道'
      : '学生端无需消息通道';
    return;
  }
  if (appState.role === 'student') {
    $('#socketStatus').textContent = '学生端无需消息通道';
  }
}

function saveLogin(data) {
  appState.user = { id: data.id, name: data.name, role: data.role };
  localStorage.setItem('rehab-token', data.token);
  localStorage.setItem('rehab-user', JSON.stringify(appState.user));
  localStorage.setItem('rehab-role', appState.role);
  updateAccount();
  renderNav();
  connectWebSocket();
}

async function logout() {
  try {
    await RehabApi.logout();
  } catch (error) {
    // 即使网络不可用，也应立即清理浏览器中的访问凭证。
  }
  clearLocalSession();
  toast('已退出登录');
}

function clearLocalSession() {
  localStorage.removeItem('rehab-token');
  localStorage.removeItem('rehab-user');
  appState.user = null;
  if (appState.ws) {
    appState.ws.onclose = null;
    appState.ws.close();
    appState.ws = null;
  }
  $('#passwordInput').value = '';
  renderNav();
  updateAccount();
  updateConnectionStatus();
  showView('loginView');
}

async function handleLogin(event) {
  event.preventDefault();
  const phone = $('#phoneInput').value.trim();
  const password = $('#passwordInput').value.trim();
  if (!phone || !password) {
    toast('请输入手机号和密码');
    return;
  }
  try {
    const loginApi = appState.role === 'student' ? RehabApi.loginStudent : RehabApi.loginTeacher;
    const data = await loginApi({ phone, password });
    saveLogin(data);
    toast('登录成功');
    showView(appState.role === 'student' ? 'studentCoursesView' : 'teacherDashboardView');
  } catch (error) {
    toast(error.message);
  }
}

async function loadStudentData() {
  try {
    const [courses, teachers] = await Promise.all([RehabApi.listStudentCourses(), RehabApi.listTeachers()]);
    renderCourses(courses || []);
    renderTeachers(teachers || []);
    renderSelectedInfo();
  } catch (error) {
    toast(error.message);
  }
}

function renderCourses(courses) {
  $('#courseList').innerHTML = courses.length ? courses.map(course => `
    <div class="data-card ${appState.selectedCourse && appState.selectedCourse.id === course.id ? 'selected' : ''}" data-course-id="${course.id}">
      <div class="card-title"><h5>${escapeHtml(course.name)}</h5><span class="tag">${escapeHtml(course.category)}</span></div>
      <p>${escapeHtml(course.trainingGoal || '暂无训练目标')}</p>
      <div class="meta-line">
        <span class="tag">${course.durationMinutes || '-'} 分钟</span>
        <span class="tag">￥${course.price || 0}</span>
      </div>
    </div>
  `).join('') : emptyHtml('暂无课程');
  $('#courseList').querySelectorAll('[data-course-id]').forEach(card => {
    card.addEventListener('click', () => {
      appState.selectedCourse = courses.find(course => String(course.id) === card.dataset.courseId);
      renderCourses(courses);
      renderSelectedInfo();
      if (appState.selectedTeacher) loadSchedules();
    });
  });
}

function renderTeachers(teachers) {
  $('#teacherList').innerHTML = teachers.length ? teachers.map(teacher => `
    <div class="data-card ${appState.selectedTeacher && appState.selectedTeacher.id === teacher.id ? 'selected' : ''}" data-teacher-id="${teacher.id}">
      <div class="card-title"><h5>${escapeHtml(teacher.name)}</h5><span class="tag">ID ${teacher.id}</span></div>
      <p>${escapeHtml(teacher.specialty || '康复训练')}</p>
      <p>${escapeHtml(teacher.introduction || '暂无简介')}</p>
    </div>
  `).join('') : emptyHtml('暂无教师');
  $('#teacherList').querySelectorAll('[data-teacher-id]').forEach(card => {
    card.addEventListener('click', () => {
      appState.selectedTeacher = teachers.find(teacher => String(teacher.id) === card.dataset.teacherId);
      renderTeachers(teachers);
      renderSelectedInfo();
      if (appState.selectedCourse) loadSchedules();
    });
  });
}

function renderSelectedInfo() {
  const course = appState.selectedCourse ? appState.selectedCourse.name : '未选择课程';
  const teacher = appState.selectedTeacher ? appState.selectedTeacher.name : '未选择教师';
  $('#selectedInfo').textContent = `当前选择：${course} / ${teacher}`;
}

async function loadSchedules() {
  try {
    const schedules = await RehabApi.listSchedules(appState.selectedTeacher.id);
    $('#scheduleList').innerHTML = schedules && schedules.length ? schedules.map(schedule => `
      <div class="schedule-card">
        <div class="card-title">
          <h5>${formatDateTime(schedule.startTime)} - ${formatTime(schedule.endTime)}</h5>
          <button class="primary-btn" data-schedule-id="${schedule.id}">立即预约</button>
        </div>
        <p>排班 ID：${schedule.id}，教师 ID：${schedule.teacherId}</p>
      </div>
    `).join('') : emptyHtml('暂无可预约时间');
    $('#scheduleList').querySelectorAll('[data-schedule-id]').forEach(button => {
      button.addEventListener('click', () => submitAppointment(button.dataset.scheduleId));
    });
  } catch (error) {
    toast(error.message);
  }
}

async function submitAppointment(scheduleId) {
  if (!appState.selectedCourse || !appState.selectedTeacher) {
    toast('请先选择课程和教师');
    return;
  }
  try {
    const result = await RehabApi.submitAppointment({
      teacherId: appState.selectedTeacher.id,
      courseId: appState.selectedCourse.id,
      scheduleId
    });
    toast(`预约提交成功：${result.appointmentNo}`);
    pollSubmitStatus(result.appointmentId);
    loadSchedules();
  } catch (error) {
    toast(error.message);
  }
}

async function pollSubmitStatus(appointmentId) {
  let count = 0;
  const timer = setInterval(async () => {
    count += 1;
    try {
      const status = await RehabApi.getSubmitStatus(appointmentId);
      if (status !== 'PROCESSING' || count >= 6) {
        clearInterval(timer);
        toast(`预约处理状态：${status}`);
        loadStudentAppointments();
      }
    } catch (error) {
      clearInterval(timer);
    }
  }, 1200);
}

async function loadStudentAppointments() {
  try {
    const appointments = await RehabApi.listStudentAppointments();
    $('#studentAppointmentList').innerHTML = renderAppointmentRows(appointments || [], 'student');
    bindStudentAppointmentActions();
  } catch (error) {
    toast(error.message);
  }
}

async function loadTeacherAppointments() {
  try {
    const appointments = await RehabApi.listAdminAppointments();
    $('#teacherAppointmentList').innerHTML = renderAppointmentRows(appointments || [], 'teacher');
    bindTeacherAppointmentActions();
  } catch (error) {
    toast(error.message);
  }
}

function renderAppointmentRows(appointments, mode) {
  if (!appointments.length) return emptyHtml('暂无预约记录');
  return appointments.map(item => {
    const partyLabel = mode === 'teacher' ? '预约学生' : '康复教师';
    const partyName = mode === 'teacher' ? item.studentName : item.teacherName;
    return `
      <article class="table-row appointment-card">
        <div class="card-title">
          <div>
            <p class="appointment-category">${escapeHtml(item.courseCategory || '康复课程')}</p>
            <h5>${escapeHtml(item.courseName || '课程信息待补充')}</h5>
          </div>
          <span class="status-tag status-${item.status}">${statusText[item.status] || '未知'}</span>
        </div>
        <p class="appointment-main-time">${formatDateTime(item.startTime)} – ${formatTime(item.endTime)}</p>
        <div class="appointment-meta">
          <span>${partyLabel}：<strong>${escapeHtml(partyName || '未填写')}</strong></span>
          <span>金额：￥${item.amount || 0}</span>
          <span>预约编号：${escapeHtml(item.appointmentNo || item.id)}</span>
        </div>
        ${item.cancelReason ? `<p class="appointment-reason">原因：${escapeHtml(item.cancelReason)}</p>` : ''}
        <div class="row-actions">${renderAppointmentActions(item, mode)}</div>
      </article>`;
  }).join('');
}

function renderAppointmentActions(item, mode) {
  if (mode === 'student' && [1, 2].includes(item.status)) {
    return `<button class="danger-btn" data-cancel-id="${item.id}">取消预约</button>`;
  }
  if (mode === 'teacher' && item.status === 1) {
    return `
      <button class="success-btn" data-confirm-id="${item.id}">确认</button>
      <button class="danger-btn" data-reject-id="${item.id}">拒绝</button>
    `;
  }
  if (mode === 'teacher' && item.status === 2) {
    return `<button class="primary-btn" data-complete-id="${item.id}">完成课程</button>`;
  }
  return '<span class="muted">无可操作项</span>';
}

function bindStudentAppointmentActions() {
  document.querySelectorAll('[data-cancel-id]').forEach(button => {
    button.addEventListener('click', async () => {
      const reason = prompt('请输入取消原因', '个人时间冲突') || '学生取消';
      try {
        await RehabApi.cancelAppointment({ appointmentId: button.dataset.cancelId, reason });
        toast('已取消预约');
        loadStudentAppointments();
      } catch (error) {
        toast(error.message);
      }
    });
  });
}

function bindTeacherAppointmentActions() {
  document.querySelectorAll('[data-confirm-id]').forEach(button => {
    button.addEventListener('click', async () => {
      try {
        await RehabApi.confirmAppointment(button.dataset.confirmId);
        toast('已确认预约');
        loadTeacherAppointments();
      } catch (error) {
        toast(error.message);
      }
    });
  });
  document.querySelectorAll('[data-reject-id]').forEach(button => {
    button.addEventListener('click', async () => {
      const reason = prompt('请输入拒绝原因', '该时段暂不可预约') || '教师拒绝';
      try {
        await RehabApi.rejectAppointment({ appointmentId: button.dataset.rejectId, reason });
        toast('已拒绝预约');
        loadTeacherAppointments();
      } catch (error) {
        toast(error.message);
      }
    });
  });
  document.querySelectorAll('[data-complete-id]').forEach(button => {
    button.addEventListener('click', async () => {
      try {
        await RehabApi.completeAppointment(button.dataset.completeId);
        toast('课程已完成');
        loadTeacherAppointments();
      } catch (error) {
        toast(error.message);
      }
    });
  });
}

async function loadAdminCourses() {
  try {
    const courses = await RehabApi.listAdminCourses();
    $('#adminCourseList').innerHTML = courses && courses.length ? courses.map(course => `
      <div class="data-card">
        <div class="card-title">
          <h5>${escapeHtml(course.name)}</h5>
          <span class="status-tag status-${course.status === 1 ? 2 : 4}">${course.status === 1 ? '启用' : '禁用'}</span>
        </div>
        <p>${escapeHtml(course.category)}｜${course.durationMinutes} 分钟｜￥${course.price}</p>
        <p>${escapeHtml(course.trainingGoal || '暂无训练目标')}</p>
        <button class="ghost-btn" data-course-status-id="${course.id}" data-next-status="${course.status === 1 ? 0 : 1}">${course.status === 1 ? '禁用' : '启用'}</button>
      </div>
    `).join('') : emptyHtml('暂无课程');
    document.querySelectorAll('[data-course-status-id]').forEach(button => {
      button.addEventListener('click', async () => {
        try {
          await RehabApi.updateCourseStatus(button.dataset.courseStatusId, button.dataset.nextStatus);
          toast('课程状态已更新');
          loadAdminCourses();
        } catch (error) {
          toast(error.message);
        }
      });
    });
  } catch (error) {
    toast(error.message);
  }
}

async function handleCreateCourse(event) {
  event.preventDefault();
  const formData = new FormData(event.target);
  const data = Object.fromEntries(formData.entries());
  data.durationMinutes = Number(data.durationMinutes);
  data.price = Number(data.price);
  try {
    await RehabApi.createCourse(data);
    event.target.reset();
    toast('课程保存成功');
    loadAdminCourses();
  } catch (error) {
    toast(error.message);
  }
}

async function loadAdminTeachers() {
  try {
    const teachers = await RehabApi.listAdminTeachers();
    renderAdminTeachers(teachers || []);
  } catch (error) {
    toast(error.message);
  }
}

function renderAdminTeachers(teachers) {
  const container = $('#adminTeacherList');
  if (!teachers.length) {
    container.innerHTML = emptyHtml('暂无教师账号');
    return;
  }
  container.innerHTML = teachers.map(teacher => {
    const enabled = Number(teacher.status) === 1;
    const isCurrentAdmin = teacher.role === 'admin' && String(teacher.id) === String(appState.user && appState.user.id);
    return `
      <article class="data-card teacher-admin-card">
        <div class="card-title">
          <div>
            <h5>${escapeHtml(teacher.name)}</h5>
            <p class="teacher-phone">${escapeHtml(teacher.phone)}</p>
          </div>
          <span class="status-tag status-${enabled ? 2 : 4}">${enabled ? '在职启用' : '已停用'}</span>
        </div>
        <p><strong>${teacher.role === 'admin' ? '管理员' : '康复教师'}</strong> · ${escapeHtml(teacher.specialty || '暂未填写擅长方向')}</p>
        <p>${escapeHtml(teacher.introduction || '暂无教师简介')}</p>
        <div class="row-actions teacher-card-actions">
          <span class="muted">账号 ID ${escapeHtml(teacher.id)}</span>
          <button class="${enabled ? 'danger-btn' : 'success-btn'}" data-teacher-status-id="${teacher.id}" data-next-status="${enabled ? 0 : 1}" ${isCurrentAdmin ? 'disabled title="不能停用当前管理员"' : ''}>${enabled ? '停用账号' : '重新启用'}</button>
        </div>
      </article>`;
  }).join('');
  container.querySelectorAll('[data-teacher-status-id]:not([disabled])').forEach(button => {
    button.addEventListener('click', async () => {
      try {
        await RehabApi.updateTeacherStatus(button.dataset.teacherStatusId, button.dataset.nextStatus);
        toast(button.dataset.nextStatus === '1' ? '教师账号已启用' : '教师账号已停用');
        loadAdminTeachers();
      } catch (error) {
        toast(error.message);
      }
    });
  });
}

async function handleCreateTeacher(event) {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.target).entries());
  try {
    await RehabApi.createTeacher(data);
    event.target.reset();
    toast('教师账号创建成功');
    loadAdminTeachers();
  } catch (error) {
    toast(error.message);
  }
}

async function handleCreateSchedule(event) {
  event.preventDefault();
  const formData = new FormData(event.target);
  const data = Object.fromEntries(formData.entries());
  data.teacherId = Number(data.teacherId);
  data.startTime = normalizeDateTime(data.startTime);
  data.endTime = normalizeDateTime(data.endTime);
  try {
    if (appState.editingScheduleId) {
      await RehabApi.updateSchedule(appState.editingScheduleId, {
        startTime: data.startTime,
        endTime: data.endTime
      });
      toast('排班时间已修改');
    } else {
      await RehabApi.createSchedule(data);
      toast('排班保存成功');
    }
    resetScheduleForm();
    loadTeacherSchedules();
  } catch (error) {
    toast(error.message);
  }
}

async function loadTeacherSchedules() {
  if (appState.user) {
    $('#scheduleForm').elements.teacherId.value = appState.user.id;
  }
  try {
    const schedules = await RehabApi.listAdminSchedules();
    renderTeacherSchedules(schedules || []);
  } catch (error) {
    toast(error.message);
  }
}

function renderTeacherSchedules(schedules) {
  const container = $('#teacherScheduleList');
  if (!schedules.length) {
    container.innerHTML = emptyHtml('还没有排班，请先在左侧新增可预约时间。');
    return;
  }
  container.innerHTML = schedules.map(item => {
    const state = getTeacherScheduleState(item);
    const isPast = new Date(item.endTime).getTime() < Date.now();
    const isFuture = new Date(item.startTime).getTime() > Date.now();
    const pastClass = isPast ? ' past' : '';
    const courseDetail = item.appointmentId ? `
      <div class="booked-course-detail">
        <div>
          <span class="detail-label">预约课程</span>
          <strong>${escapeHtml(item.courseName || ('课程 ID ' + item.courseId))}</strong>
          <small>${escapeHtml(item.courseCategory || '康复课程')}</small>
        </div>
        <div class="schedule-booking-meta">
          <span>学生 ID：${escapeHtml(item.studentId)}</span>
          <span>预约编号：${escapeHtml(item.appointmentNo)}</span>
        </div>
      </div>` : `
      <div class="available-schedule-detail">
        该时间段尚未被预约，学生端可以选择此时段。
      </div>`;
    const scheduleStatus = Number(item.scheduleStatus);
    const canManage = !item.appointmentId && [1, 3].includes(scheduleStatus);
    const managementActions = canManage ? `
      <div class="schedule-actions">
        ${isFuture ? `<button class="ghost-btn" data-edit-schedule-id="${item.id}">修改时间</button>` : ''}
        ${scheduleStatus === 1 ? `<button class="danger-btn" data-toggle-schedule-id="${item.id}" data-next-status="3">停用</button>` : ''}
        ${scheduleStatus === 3 && isFuture ? `<button class="success-btn" data-toggle-schedule-id="${item.id}" data-next-status="1">启用</button>` : ''}
        ${scheduleStatus === 3 ? `<button class="danger-text-btn" data-delete-schedule-id="${item.id}">删除</button>` : ''}
      </div>` : '';
    return `
      <article class="teacher-schedule-card ${state.className}${pastClass}">
        <div class="schedule-time-rail" aria-hidden="true"></div>
        <div class="teacher-schedule-content">
          <div class="card-title">
            <div>
              <p class="schedule-day">${formatScheduleDay(item.startTime)}</p>
              <h5>${formatTime(item.startTime)} – ${formatTime(item.endTime)}</h5>
            </div>
            <span class="schedule-state ${state.className}">${state.label}</span>
          </div>
          ${courseDetail}
          ${managementActions}
        </div>
      </article>`;
  }).join('');
  bindScheduleActions(schedules);
}

function bindScheduleActions(schedules) {
  document.querySelectorAll('[data-edit-schedule-id]').forEach(button => {
    button.addEventListener('click', () => {
      const schedule = schedules.find(item => String(item.id) === button.dataset.editScheduleId);
      if (schedule) startScheduleEdit(schedule);
    });
  });
  document.querySelectorAll('[data-toggle-schedule-id]').forEach(button => {
    button.addEventListener('click', async () => {
      try {
        await RehabApi.updateScheduleStatus(button.dataset.toggleScheduleId, button.dataset.nextStatus);
        toast(button.dataset.nextStatus === '3' ? '排班已停用' : '排班已重新启用');
        if (String(appState.editingScheduleId) === button.dataset.toggleScheduleId) resetScheduleForm();
        loadTeacherSchedules();
      } catch (error) {
        toast(error.message);
      }
    });
  });
  document.querySelectorAll('[data-delete-schedule-id]').forEach(button => {
    button.addEventListener('click', async () => {
      if (!confirm('确定永久删除这个已停用排班吗？有预约历史的排班不会被删除。')) return;
      try {
        await RehabApi.deleteSchedule(button.dataset.deleteScheduleId);
        if (String(appState.editingScheduleId) === button.dataset.deleteScheduleId) resetScheduleForm();
        toast('排班已删除');
        loadTeacherSchedules();
      } catch (error) {
        toast(error.message);
      }
    });
  });
}

function startScheduleEdit(schedule) {
  appState.editingScheduleId = schedule.id;
  const form = $('#scheduleForm');
  form.elements.startTime.value = toDateTimeLocal(schedule.startTime);
  form.elements.endTime.value = toDateTimeLocal(schedule.endTime);
  $('#scheduleSubmitBtn').textContent = '保存修改';
  $('#cancelScheduleEditBtn').hidden = false;
  form.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function resetScheduleForm() {
  appState.editingScheduleId = null;
  const form = $('#scheduleForm');
  form.reset();
  if (appState.user) form.elements.teacherId.value = appState.user.id;
  $('#scheduleSubmitBtn').textContent = '保存排班';
  $('#cancelScheduleEditBtn').hidden = true;
}

function getTeacherScheduleState(item) {
  const appointmentStatus = Number(item.appointmentStatus);
  if (appointmentStatus === 1) return { label: '待确认', className: 'pending' };
  if (appointmentStatus === 2) return { label: '已有课程', className: 'confirmed' };
  if (appointmentStatus === 3) return { label: '已完成', className: 'completed' };
  if (Number(item.scheduleStatus) === 1) return { label: '可预约', className: 'available' };
  if (Number(item.scheduleStatus) === 3) return { label: '已停用', className: 'disabled' };
  return { label: '已占用', className: 'confirmed' };
}

function formatScheduleDay(value) {
  if (!value) return '日期待定';
  const text = String(value);
  const date = text.slice(0, 10);
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
  const parsed = new Date(`${date}T00:00:00`);
  const weekday = Number.isNaN(parsed.getTime()) ? '' : ` · ${weekdays[parsed.getDay()]}`;
  return `${date.replace(/-/g, '/')} ${weekday}`;
}

function connectWebSocket() {
  if (appState.ws) {
    appState.ws.onclose = null;
    appState.ws.close();
    appState.ws = null;
  }
  if (!localStorage.getItem('rehab-token') || !appState.user) {
    updateConnectionStatus();
    return;
  }
  if (appState.role !== 'teacher') {
    $('#socketStatus').textContent = '学生端无需消息通道';
    return;
  }
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const config = window.REHAB_CONFIG || {};
  const wsPath = config.wsPath || '/ws/appointment';
  const apiBase = (config.apiBase || '').replace(/\/$/, '');
  let wsUrl;
  if (apiBase.startsWith('http')) {
    wsUrl = apiBase.replace(/^http/, 'ws') + wsPath;
  } else {
    wsUrl = `${protocol}//${location.host}${wsPath}`;
  }
  try {
    const token = localStorage.getItem('rehab-token') || '';
    appState.ws = new WebSocket(wsUrl, ['rehab', token]);
    appState.ws.onopen = () => $('#socketStatus').textContent = '消息通道已连接';
    appState.ws.onclose = () => $('#socketStatus').textContent = '消息通道未连接';
    appState.ws.onerror = () => $('#socketStatus').textContent = '消息通道连接异常';
    appState.ws.onmessage = event => {
      $('#noticeBoard').textContent = event.data;
      toast(event.data);
      if (appState.role === 'teacher') loadTeacherAppointments();
    };
  } catch (error) {
    $('#socketStatus').textContent = '消息通道连接失败';
  }
}

function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function emptyHtml(text) {
  return `<div class="empty">${escapeHtml(text)}</div>`;
}

function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
}

function formatTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(11, 16);
}

function normalizeDateTime(value) {
  return value ? `${value}:00` : value;
}

function toDateTimeLocal(value) {
  return value ? String(value).replace(' ', 'T').slice(0, 16) : '';
}

function init() {
  setRole(appState.role);
  $('#loginForm').addEventListener('submit', handleLogin);
  $('#logoutBtn').addEventListener('click', logout);
  $('#refreshStudentBtn').addEventListener('click', loadStudentData);
  $('#refreshStudentAppointmentsBtn').addEventListener('click', loadStudentAppointments);
  $('#refreshTeacherBtn').addEventListener('click', loadTeacherAppointments);
  $('#refreshCoursesBtn').addEventListener('click', loadAdminCourses);
  $('#refreshTeachersBtn').addEventListener('click', loadAdminTeachers);
  $('#refreshSchedulesBtn').addEventListener('click', loadTeacherSchedules);
  $('#courseForm').addEventListener('submit', handleCreateCourse);
  $('#teacherForm').addEventListener('submit', handleCreateTeacher);
  $('#scheduleForm').addEventListener('submit', handleCreateSchedule);
  $('#cancelScheduleEditBtn').addEventListener('click', resetScheduleForm);
  document.querySelectorAll('.role-btn').forEach(button => button.addEventListener('click', () => setRole(button.dataset.role)));
  if (localStorage.getItem('rehab-token')) {
    connectWebSocket();
    showView(appState.role === 'student' ? 'studentCoursesView' : 'teacherDashboardView');
  }
}

document.addEventListener('DOMContentLoaded', init);
window.addEventListener('rehab-auth-expired', clearLocalSession);
window.addEventListener('rehab-token-refreshed', () => connectWebSocket());
