import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { userAdminApi } from '../userAdminApi'
import type { CreateUser, UpdateUser } from '../types'
export function useUsers(page: number) {
  return useQuery({ queryKey: ['users', page], queryFn: ({ signal }) => userAdminApi.users(page, signal) })
}
export function useRoles() {
  return useQuery({ queryKey: ['roles'], queryFn: ({ signal }) => userAdminApi.roles(signal) })
}
export function useCreateUser() {
  const client = useQueryClient()
  return useMutation({ mutationFn: (data: CreateUser) => userAdminApi.create(data), onSuccess: () => client.invalidateQueries({ queryKey: ['users'] }) })
}
export function useUpdateUser(id?: number) {
  const client = useQueryClient()
  return useMutation({ mutationFn: (data: UpdateUser) => userAdminApi.update(id!, data), onSuccess: () => client.invalidateQueries({ queryKey: ['users'] }) })
}
export function useResetPassword(id: number) {
  const client = useQueryClient()
  return useMutation({ mutationFn: (password: string) => userAdminApi.resetPassword(id, password), onSuccess: () => client.invalidateQueries({ queryKey: ['users'] }) })
}
