import { apiRequest } from './request.js';

export const listUsers = () =>
  apiRequest('/admin/users');

export const createUser = (payload) =>
  apiRequest('/admin/users', {
    method: 'POST',
    body: JSON.stringify(payload),
  });

export const updateUser = (userId, payload) =>
  apiRequest(`/admin/users/${userId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });

export const changePassword = (userId, password) =>
  apiRequest(`/admin/users/${userId}/password`, {
    method: 'PUT',
    body: JSON.stringify({ password }),
  });

export const deleteUser = (userId) =>
  apiRequest(`/admin/users/${userId}`, {
    method: 'DELETE',
  });
