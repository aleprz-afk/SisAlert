using MiniIdentityApi.Domain.Entities;

namespace MiniIdentityApi.Application.Interfaces;

public interface IRoleRepository
{
    Role? FindByName(string name);
    List<Role> GetAll();
    void Save(Role role);
}