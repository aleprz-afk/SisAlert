using MiniIdentityApi.Domain.Entities;

namespace MiniIdentityApi.Application.Interfaces;

public interface ITokenService
{
    string GenerateToken(User user);
}