using Microsoft.EntityFrameworkCore;
using MiniIdentityApi.Application.Interfaces;
using MiniIdentityApi.Domain.Entities;
using MiniIdentityApi.Infrastructure.Persistence;

namespace MiniIdentityApi.Infrastructure.Repositories;

/// <summary>Repositorio de usuarios respaldado por PostgreSQL (EF Core).</summary>
public class EfUserRepository : IUserRepository
{
    private readonly IdentityDbContext _context;

    public EfUserRepository(IdentityDbContext context)
    {
        _context = context;
    }

    public User? FindById(Guid id)
    {
        return _context.Users
            .AsSplitQuery()
            .Include(u => u.Roles)
            .ThenInclude(r => r.Permissions)
            .FirstOrDefault(u => u.Id == id);
    }

    public User? FindByUsernameOrEmail(string value)
    {
        var buscado = value.Trim().ToLower();
        return _context.Users
            .AsSplitQuery()
            .Include(u => u.Roles)
            .ThenInclude(r => r.Permissions)
            .FirstOrDefault(u => u.Username.ToLower() == buscado || u.Email.ToLower() == buscado);
    }

    public List<User> GetAll()
    {
        return _context.Users
            .AsNoTracking()
            .AsSplitQuery()
            .Include(u => u.Roles)
            .ThenInclude(r => r.Permissions)
            .OrderBy(u => u.Username)
            .ToList();
    }

    public void Save(User user)
    {
        var entry = _context.Entry(user);
        if (entry.State == EntityState.Detached)
        {
            _context.Users.Add(user);
        }

        _context.SaveChanges();
    }

    public void Delete(User user)
    {
        _context.Users.Remove(user);
        _context.SaveChanges();
    }
}

