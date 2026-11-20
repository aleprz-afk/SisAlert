using MiniIdentityApi.Application.Interfaces;
using MiniIdentityApi.Domain.Entities;

namespace MiniIdentityApi.Application.Services;

public class RoleService
{
    private readonly IRoleRepository _roleRepository;

    public RoleService(IRoleRepository roleRepository)
    {
        _roleRepository = roleRepository;
    }

    public List<Role> GetAll() => _roleRepository.GetAll();

    public void Create(string name)
    {
        if (string.IsNullOrWhiteSpace(name))
            throw new ArgumentException("Role name is required.");

        if (_roleRepository.FindByName(name) is not null)
            throw new InvalidOperationException("Role already exists.");

        _roleRepository.Save(new Role(name));
    }

    public void AddPermission(string roleName, string code, string description)
    {
        var role = _roleRepository.FindByName(roleName)
                   ?? throw new KeyNotFoundException("Role not found.");

        role.AddPermission(new Permission(code, description));
        _roleRepository.Save(role);
    }
}