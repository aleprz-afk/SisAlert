using MiniIdentityApi.Application.Interfaces;
using MiniIdentityApi.Domain.Entities;

namespace MiniIdentityApi.Infrastructure.Repositories;

/// <summary>Repositorio en memoria conservado para las pruebas unitarias.</summary>
public class InMemoryRoleRepository : IRoleRepository
{
    private readonly List<Role> _roles = new();

    public Role? FindByName(string name)
    {
        return _roles.FirstOrDefault(r =>
            r.Name.Equals(name, StringComparison.OrdinalIgnoreCase));
    }

    public List<Role> GetAll() => _roles;

    public void Save(Role role)
    {
        var index = _roles.FindIndex(r => r.Id == role.Id);
        if (index >= 0)
            _roles[index] = role;
        else
            _roles.Add(role);
    }
}
