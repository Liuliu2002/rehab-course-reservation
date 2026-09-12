const RehabApi = (() => {
  const config = window.REHAB_CONFIG || {};
  const apiBase = (config.apiBase || '').replace(/\/$/, '');

  function getToken() {
    return localStorage.getItem('rehab-token') || '';
  }

  let refreshPromise = null;

  async function parseResponse(response) {
    const result = await response.json().catch(() => ({ code: 0, message: '接口返回格式异常' }));
    if (!response.ok || result.code === 0) {
      const error = new Error(result.message || (response.status === 401 ? '登录已过期，请重新登录' : '请求失败'));
      error.status = response.status;
      throw error;
    }
    return result.data;
  }

  async function refreshSession() {
    if (!refreshPromise) {
      refreshPromise = (async () => {
        const role = localStorage.getItem('rehab-role') || 'student';
        const prefix = role === 'student' ? '/student' : '/admin';
        const response = await fetch(apiBase + prefix + '/refresh', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' }
        });
        const data = await parseResponse(response);
        localStorage.setItem('rehab-token', data.token);
        window.dispatchEvent(new CustomEvent('rehab-token-refreshed', { detail: data }));
        return data;
      })().finally(() => {
        refreshPromise = null;
      });
    }
    return refreshPromise;
  }

  async function request(path, options = {}, retryAfterRefresh = true) {
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    const token = getToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    const response = await fetch(apiBase + path, Object.assign({}, options, {
      headers,
      credentials: 'include'
    }));
    if (response.status === 401 && retryAfterRefresh && token
      && !path.endsWith('/login') && !path.endsWith('/refresh')) {
      try {
        await refreshSession();
        return request(path, options, false);
      } catch (error) {
        window.dispatchEvent(new CustomEvent('rehab-auth-expired'));
        throw new Error('登录已过期，请重新登录');
      }
    }
    if (response.status === 403) {
      throw new Error('当前角色没有权限访问该功能');
    }
    return parseResponse(response);
  }

  function post(path, body) {
    return request(path, { method: 'POST', body: JSON.stringify(body || {}) });
  }

  function put(path, body) {
    const options = { method: 'PUT' };
    if (body) {
      options.body = JSON.stringify(body);
    }
    return request(path, options);
  }

  function del(path) {
    return request(path, { method: 'DELETE' });
  }

  return {
    config,
    apiBase,
    request,
    post,
    put,
    loginStudent: data => post('/student/login', data),
    loginTeacher: data => post('/admin/login', data),
    refreshSession,
    logout: () => post(`/${(localStorage.getItem('rehab-role') || 'student') === 'student' ? 'student' : 'admin'}/logout`),
    listStudentCourses: () => request('/student/course'),
    listTeachers: () => request('/student/teacher'),
    listSchedules: teacherId => request(`/student/course/schedule?teacherId=${teacherId}`),
    submitAppointment: data => post('/student/appointment', data),
    getSubmitStatus: id => request(`/student/appointment/status/${id}`),
    listStudentAppointments: () => request('/student/appointment'),
    cancelAppointment: data => post('/student/appointment/cancel', data),
    listAdminAppointments: () => request('/admin/appointment'),
    confirmAppointment: id => put(`/admin/appointment/${id}/confirm`),
    rejectAppointment: data => post('/admin/appointment/reject', data),
    completeAppointment: id => put(`/admin/appointment/${id}/complete`),
    listAdminCourses: () => request('/admin/course'),
    createCourse: data => post('/admin/course', data),
    updateCourseStatus: (id, status) => put(`/admin/course/${id}/status?status=${status}`),
    listAdminTeachers: () => request('/admin/teacher'),
    createTeacher: data => post('/admin/teacher', data),
    updateTeacherStatus: (id, status) => put(`/admin/teacher/${id}/status?status=${status}`),
    createSchedule: data => post('/admin/schedule', data),
    updateSchedule: (id, data) => put(`/admin/schedule/${id}`, data),
    updateScheduleStatus: (id, status) => put(`/admin/schedule/${id}/status?status=${status}`),
    deleteSchedule: id => del(`/admin/schedule/${id}`),
    listAdminSchedules: () => request('/admin/schedule')
  };
})();
