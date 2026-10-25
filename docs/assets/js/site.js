// The theme switch, the menu on small screens, and what the documentation pages get on top of
// their Markdown: links to their headings, a table of contents, and copy buttons on code.
(() => {
  const THEME_KEY = 'ldes-admin-docs.theme'
  const root = document.documentElement

  // Theme ---------------------------------------------------------------------------------------

  const themeToggle = document.querySelector('[data-theme-toggle]')

  function showTheme(theme) {
    root.dataset.theme = theme
    const label = theme === 'dark' ? 'Switch to the white theme' : 'Switch to the black theme'
    themeToggle?.setAttribute('aria-label', label)
    themeToggle?.setAttribute('title', label)
  }

  showTheme(root.dataset.theme === 'light' ? 'light' : 'dark')
  themeToggle?.addEventListener('click', () => {
    const theme = root.dataset.theme === 'dark' ? 'light' : 'dark'
    showTheme(theme)
    try {
      localStorage.setItem(THEME_KEY, theme)
    } catch (e) {
      // Not remembered, but switched all the same
    }
  })

  // Menu on small screens -----------------------------------------------------------------------

  const header = document.querySelector('.site-header')
  const navToggle = document.querySelector('[data-nav-toggle]')
  navToggle?.addEventListener('click', () => {
    const open = header.classList.toggle('nav-open')
    navToggle.setAttribute('aria-expanded', String(open))
  })

  // Documentation pages -------------------------------------------------------------------------

  const prose = document.querySelector('.prose')
  if (!prose) return

  const headings = [...prose.querySelectorAll('h2[id], h3[id]')]
  headings.forEach((heading) => {
    const anchor = document.createElement('a')
    anchor.className = 'heading-anchor'
    anchor.href = `#${heading.id}`
    anchor.textContent = '#'
    anchor.setAttribute('aria-label', `Link to “${heading.textContent}”`)
    heading.prepend(anchor)
  })

  const toc = document.querySelector('[data-toc]')
  if (toc && headings.length > 1) {
    const list = document.createElement('ul')
    const links = new Map()
    headings.forEach((heading) => {
      const item = document.createElement('li')
      const link = document.createElement('a')
      link.href = `#${heading.id}`
      link.className = `level-${heading.tagName.substring(1)}`
      // Without the # of the heading anchor
      link.textContent = heading.textContent.replace(/^#/, '')
      item.append(link)
      list.append(item)
      links.set(heading, link)
    })
    toc.querySelector('nav').append(list)
    toc.hidden = false

    // The section being read: the last heading above the top fifth of the window
    const highlight = () => {
      const line = window.innerHeight / 5
      const current = headings.filter((heading) => heading.getBoundingClientRect().top < line).pop()
        ?? headings[0]
      links.forEach((link, heading) => link.classList.toggle('active', heading === current))
    }
    let scheduled = false
    window.addEventListener('scroll', () => {
      if (scheduled) return
      scheduled = true
      requestAnimationFrame(() => {
        scheduled = false
        highlight()
      })
    }, { passive: true })
    highlight()
  }

  if (navigator.clipboard) {
    prose.querySelectorAll('div.highlighter-rouge').forEach((block) => {
      const button = document.createElement('button')
      button.type = 'button'
      button.className = 'copy-button'
      button.textContent = 'Copy'
      button.addEventListener('click', async () => {
        await navigator.clipboard.writeText(block.querySelector('code').innerText.trimEnd())
        button.textContent = 'Copied'
        button.classList.add('copied')
        setTimeout(() => {
          button.textContent = 'Copy'
          button.classList.remove('copied')
        }, 1600)
      })
      block.append(button)
    })
  }
})()
