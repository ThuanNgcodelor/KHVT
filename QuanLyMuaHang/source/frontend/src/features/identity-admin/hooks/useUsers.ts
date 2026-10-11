import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { userAdminApi } from '../userAdminApi'
import type { CreateUser, UpdateUser, UserFilters } from '../types'
export function useUsers(filters: UserFilters) {
  return useQuery({ queryKey: ['users', 'list', filters], queryFn: ({ signal }) => userAdminApi.users(filters, signal) })
}
export function useUserOptions() {
  return useQuery({ queryKey: ['users', 'options'], queryFn: ({ signal }) => userAdminApi.options(signal) })
}
export function useRoles(enabled = true) {
  return useQuery({ queryKey: ['roles'], queryFn: ({ signal }) => userAdminApi.roles(signal), enabled })
}
export function useCreateUser() {
  const client = useQueryClient()
  return useMutation({ mutationFn: (data: CreateUser) => userAdminApi.create(data), onSuccess: async () => { await Promise.all([client.invalidateQueries({ queryKey: ['users'] }), client.invalidateQueries({ queryKey: ['personnel'] })]) } })
}
export function useUpdateUser(id?: number) {
  const client = useQueryClient()
  return useMutation({ mutationFn: (data: UpdateUser) => userAdminApi.update(id!, data), onSuccess: async () => { await Promise.all([client.invalidateQueries({ queryKey: ['users'] }), client.invalidateQueries({ queryKey: ['personnel'] })]) } })
}
export function useResetPassword(id: number) {
  const client = useQueryClient()
  return useMutation({ mutationFn: (password: string) => userAdminApi.resetPassword(id, password), onSuccess: () => client.invalidateQueries({ queryKey: ['users'] }) })
}
