using Microsoft.EntityFrameworkCore;
using MiniIdentityApi.Application.Interfaces;
using MiniIdentityApi.Domain.Entities;
using MiniIdentityApi.Infrastructure.Persistence;

namespace MiniIdentityApi.Infrastructure.Repositories;

/// <summary>Repositorio de roles respaldado por PostgreSQL (EF Core).</summary>
public class EfRoleRepository : IRoleRepository
{
    private readonly IdentityDbContext _context;

    public EfRoleRepository(IdentityDbContext context)
    {
        _context = context;
    }

    public Role? FindByName(string name)
    {
        var buscado = name.Trim().ToLower();
        return _context.Roles
            .Include(r => r.Permissions)
            .FirstOrDefault(r => r.Name.ToLower() == buscado);
    }

    public List<Role> GetAll()
    {
        return _context.Roles
            .AsNoTracking()
            .Include(r => r.Permissions)
            .OrderBy(r => r.Name)
            .ToList();
    }

    public void Save(Role role)
    {
        var entry = _context.Entry(role);
        if (entry.State == EntityState.Detached)
        {
            _context.Roles.Add(role);
        }

        // Un permiso nuevo agregado a la colección de un rol ya rastreado no se
        // detecta como "Added" porque su Id (Guid) ya viene generado. Se agrega
        // explícitamente para que EF lo inserte antes de la tabla puente.
        foreach (var permission in role.Permissions)
        {
            if (_context.Entry(permission).State == EntityState.Detached)
            {
                _context.Permissions.Add(permission);
            }
        }

        _context.SaveChanges();
    }
}
